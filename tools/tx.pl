#!/usr/bin/env perl

# v4.0.2

use JSON;
use warnings;
use strict;

use Getopt::Std;
use File::Basename;
use Digest::SHA qw( sha256 sha256_hex );
use Crypt::RIPEMD160;

our %opt;
getopts('dpst', \%opt);

my $proc = basename($0);
my $dirname = dirname($0);
my $OPENSSL_SIGN = "${dirname}/openssl-sign.sh";
my $OPENSSL_PRIV_TO_PUB = index(`$ENV{SHELL} -i -c 'openssl version;exit' 2>/dev/null`, 'OpenSSL 3.') != -1;

if (@ARGV < 1) {
	print STDERR "usage: $proc [-d] [-p] [-s] [-t] <tx-type> <privkey> <values> [<key-value pairs>]\n";
	print STDERR "-d: debug, -p: process (broadcast) transaction, -s: sign, -t: testnet\n";
	print STDERR "example: $proc PAYMENT P22kW91AJfDNBj32nVii292hhfo5AgvUYPz5W12ExsjE QxxQZiK7LZBjmpGjRz1FAZSx9MJDCoaHqz 0.1\n";
	print STDERR "example: $proc JOIN_GROUP X92h3hf9k20kBj32nVnoh3XT14o5AgvUYPz5W12ExsjE 3\n";
	print STDERR "example: BASE_URL=node10.qortal.org $proc JOIN_GROUP CB2DW91AJfd47432nVnoh3XT14o5AgvUYPz5W12ExsjE 3\n";
	print STDERR "example: $proc -p sign C4ifh827ffDNBj32nVnoh3XT14o5AgvUYPz5W12ExsjE 111jivxUwerRw...Fjtu\n";
	print STDERR "for help: $proc all\n";
	print STDERR "for help: $proc REGISTER_NAME\n";
	exit 2;
}

our @b58 = qw{
      1 2 3 4 5 6 7 8 9
    A B C D E F G H   J K L M N   P Q R S T U V W X Y Z
    a b c d e f g h i j k   m n o p q r s t u v w x y z
};
our %b58 = map { $b58[$_] => $_ } 0 .. 57;
our %reverseb58 = reverse %b58;

our $BASE_URL = $ENV{BASE_URL} || ($opt{t} ? 'http://localhost:62391' : 'http://localhost:12391');
our $DEFAULT_FEE = 0.01;

our %TRANSACTION_TYPES = (
	payment => {
		url => 'payments/pay',
		required => [qw(recipient amount)],
		key_name => 'senderPublicKey',
	},
	# groups
	set_group => {
		url => 'groups/setdefault',
		required => [qw(defaultGroupId)],
		key_name => 'creatorPublicKey',
	},
	create_group => {
		url => 'groups/create',
		required => [qw(groupName description isOpen approvalThreshold)],
		defaults => { minimumBlockDelay => 10, maximumBlockDelay => 30 },
		key_name => 'creatorPublicKey',
	},
	update_group => {
		url => 'groups/update',
		required => [qw(groupId newOwner newDescription newIsOpen newApprovalThreshold)],
		key_name => 'ownerPublicKey',
	},
	join_group => {
		url => 'groups/join',
		required => [qw(groupId)],
		key_name => 'joinerPublicKey',
	},
	leave_group => {
		url => 'groups/leave',
		required => [qw(groupId)],
		key_name => 'leaverPublicKey',
	},
	group_invite => {
		url => 'groups/invite',
		required => [qw(groupId invitee)],
		key_name => 'adminPublicKey',
	},
	group_kick => {
		url => 'groups/kick',
		required => [qw(groupId member reason)],
		key_name => 'adminPublicKey',
	},
	add_group_admin => {
		url => 'groups/addadmin',
		required => [qw(groupId txGroupId member)],
		key_name => 'ownerPublicKey',
	},
	remove_group_admin => {
		url => 'groups/removeadmin',
		required => [qw(groupId txGroupId admin)],
		key_name => 'ownerPublicKey',
	},
	group_approval => {
		url => 'groups/approval',
		required => [qw(pendingSignature approval)],
		key_name => 'adminPublicKey',
	},
	# assets
	issue_asset => {
		url => 'assets/issue',
		required => [qw(assetName description quantity isDivisible)],
		key_name => 'issuerPublicKey',
	},
	update_asset => {
		url => 'assets/update',
		required => [qw(assetId newOwner)],
		key_name => 'ownerPublicKey',
	},
	transfer_asset => {
		url => 'assets/transfer',
		required => [qw(recipient amount assetId)],
		key_name => 'senderPublicKey',
	},
	create_order => {
		url => 'assets/order',
		required => [qw(haveAssetId wantAssetId amount price)],
		key_name => 'creatorPublicKey',
	},
	# names
	register_name => {
		url => 'names/register',
		required => [qw(name data)],
		key_name => 'registrantPublicKey',
	},
	update_name => {
		url => 'names/update',
		required => [qw(name newName newData)],
		key_name => 'ownerPublicKey',
	},
	# reward-shares
	reward_share => {
		url => 'addresses/rewardshare',
		required => [qw(recipient rewardSharePublicKey sharePercent)],
		key_name => 'minterPublicKey',
	},
	# arbitrary
	arbitrary => {
		url => 'arbitrary',
		required => [qw(service dataType data)],
		key_name => 'senderPublicKey',
	},
	# chat
	chat => {
		url => 'chat',
		required => [qw(data)],
		optional => [qw(recipient isText isEncrypted)],
		key_name => 'senderPublicKey',
		defaults => { isText => 'true' },
		pow_url => 'chat/compute',
	},
	# misc
	publicize => {
		url => 'addresses/publicize',
		required => [],
		key_name => 'senderPublicKey',
		pow_url => 'addresses/publicize/compute',
	},
	# AT
	deploy_at => {
		url => 'at',
		required => [qw(name description aTType tags creationBytes amount)],
		optional => [qw(assetId)],
		key_name => 'creatorPublicKey',
		defaults => { assetId => 0 },
	},
	# Cross-chain trading
	create_trade => {
		url => 'crosschain/tradebot/create',
		required => [qw(qortAmount fundingQortAmount foreignAmount receivingAddress)],
		optional => [qw(tradeTimeout foreignBlockchain)],
		key_name => 'creatorPublicKey',
		defaults => { tradeTimeout => 1440, foreignBlockchain => 'LITECOIN' },
	},
	trade_recipient => {
		url => 'crosschain/tradeoffer/recipient',
		required => [qw(atAddress recipient)],
		key_name => 'creatorPublicKey',
		remove => [qw(timestamp reference fee)],
	},
	trade_secret => {
		url => 'crosschain/tradeoffer/secret',
		required => [qw(atAddress secret)],
		key_name => 'recipientPublicKey',
		remove => [qw(timestamp reference fee)],
	},
	# These are fake transaction types to provide utility functions:
	sign => {
		url => 'transactions/sign',
		required => [qw{transactionBytes}],
	},
);

my $tx_type = lc(shift(@ARGV));

if ($tx_type eq 'all') {
	printf STDERR "Transaction types: %s\n", join(', ', sort { $a cmp $b } keys %TRANSACTION_TYPES);
	exit 2;
}

my $tx_info = $TRANSACTION_TYPES{$tx_type};

if (!$tx_info) {
	printf STDERR "Transaction type '%s' unknown\n", uc($tx_type);
	exit 1;
}

my @required = @{$tx_info->{required}};

if (@ARGV < @required + 1) {
	printf STDERR "usage: %s %s <privkey> %s", $proc, uc($tx_type), join(' ', map { "<$_>"} @required);
	printf STDERR " %s", join(' ', map { "[$_ <$_>]" } @{$tx_info->{optional}}) if exists $tx_info->{optional};
	print "\n";
	exit 2;
}

my $priv_key = shift @ARGV;

my $account;
my $raw;

if ($tx_type ne 'sign') {
	my %extras;

	foreach my $required_arg (@required) {
		$extras{$required_arg} = shift @ARGV;
	}

	# For CHAT we use a random reference
	if ($tx_type eq 'chat') {
		$extras{reference} = api('utils/random?length=64');
	}

	%extras = (%extras, %{$tx_info->{defaults}}) if exists $tx_info->{defaults};

	%extras = (%extras, @ARGV);

	$account = account($priv_key, %extras);

	$raw = build_raw($tx_type, $account, %extras);
	printf "Raw: %s\n", $raw if $opt{d} || (!$opt{s} && !$opt{p});

	# Some transaction types require proof-of-work, e.g. CHAT
	if (exists $tx_info->{pow_url}) {
		$raw = api($tx_info->{pow_url}, $raw);
		printf "Raw with PoW: %s\n", $raw if $opt{d};
	}
} else {
	$raw = shift @ARGV;
	$opt{s}++;
}

if ($opt{s}) {
	my $signed = sign($priv_key, $raw);
	printf "Signed: %s\n", $signed if $opt{d} || $tx_type eq 'sign';

	if ($opt{p}) {
		my $processed = process($signed);
		printf "Processed: %s\n", $processed if $opt{d};
	}

	my $hex = api('utils/frombase58', $signed);
	# sig is last 64 bytes / 128 chars
	my $sighex = substr($hex, -128);

	my $sig58 = api('utils/tobase58/{hex}', '', '{hex}', $sighex);
	printf "Signature: %s\n", $sig58;
}

sub account {
	my ($privkey, %extras) = @_;

	my $account = { private => $privkey };
	$account->{public} = $extras{publickey} || priv_to_pub($privkey);
	$account->{address} = $extras{address} || pubkey_to_address($account->{public}); # api('addresses/convert/{publickey}', '', '{publickey}', $account->{public});

	return $account;
}

sub priv_to_pub {
	my ($privkey) = @_;

	if ($OPENSSL_PRIV_TO_PUB) {
		return openssl_priv_to_pub($privkey);
	} else {
		return api('utils/publickey', $privkey);
	}
}

sub build_raw {
	my ($type, $account, %extras) = @_;

	my $tx_info = $TRANSACTION_TYPES{$type};
	die("unknown tx type: $type\n") unless defined $tx_info;

	my $ref = exists $extras{reference} ? $extras{reference} : lastref($account->{address});

	my %json = (
		timestamp => time * 1000,
		reference => $ref,
		fee => $DEFAULT_FEE,
	);

	$json{$tx_info->{key_name}} = $account->{public} if exists $tx_info->{key_name}; 

	foreach my $required (@{$tx_info->{required}}) {
		die("missing tx field: $required\n") unless exists $extras{$required};
	}

	while (my ($key, $value) = each %extras) {
		$json{$key} = $value;
	}

	if (exists $tx_info->{remove}) {
		foreach my $key (@{$tx_info->{remove}}) {
			delete $json{$key};
		}
	}

	my $json = "{\n";
	while (my ($key, $value) = each %json) {
		if (ref($value) eq 'ARRAY') {
			$json .= "\t\"$key\": [],\n";
		} else {
			$json .= "\t\"$key\": \"$value\",\n";
		}
	}
	# remove final comma
	substr($json, -2, 1) = '';
	$json .= "}\n";

	printf "%s:\n%s\n", $type, $json if $opt{d};

	my $raw = api($tx_info->{url}, $json);
	return $raw;
}

sub sign {
	my ($private, $raw) = @_;

	if (-x "$OPENSSL_SIGN") {
		my $private_hex = decode_base58($private);
		chomp $private_hex;

		my $raw_hex = decode_base58($raw);
		chomp $raw_hex;

		my $sig = `${OPENSSL_SIGN} ${private_hex} ${raw_hex}`;
		chomp $sig;

		my $sig58 = encode_base58(${raw_hex} . ${sig});
		chomp $sig58;
		return $sig58;
	}

	my $json = <<"	__JSON__";
	{
		"privateKey": "$private",
		"transactionBytes": "$raw"
	}
	__JSON__

	return api('transactions/sign', $json);
}

sub process {
	my ($signed) = @_;
	
	return api('transactions/process', $signed);
}

sub lastref {
	my ($address) = @_;

	return api('addresses/lastreference/{address}', '', '{address}', $address)
}

sub api {
	my ($endpoint, $postdata, @args) = @_;

	my $url = $endpoint;
	my $method = 'GET';

	for (my $i = 0; $i < @args; $i += 2) {
		my $placemarker = $args[$i];
		my $value = $args[$i + 1];
		
		$url =~ s/$placemarker/$value/g;
	}

	my $curl = "curl --silent --output - --url '$BASE_URL/$url'";
	if (defined $postdata && $postdata ne '') {
		$postdata =~ tr|\n| |s;

		if ($postdata =~ /^\s*\{/so) {
			$curl .= " --header 'Content-Type: application/json'";
		} else {
			$curl .= " --header 'Content-Type: text/plain'";
		}

		$curl .= " --data-binary '$postdata'";
		$method = 'POST';
	}
	my $response = `$curl 2>/dev/null`; 
	chomp $response;

	if ($response eq '' || substr($response, 0, 6) eq '<html>' || $response =~ m/(^\{|,)"error":(\d+)[,}]/) {
		die("API call '$method $BASE_URL/$endpoint' failed:\n$response\n");
	}

	return $response;
}

sub encode_base58 {
    use integer;
    my @in = map { hex($_) } ($_[0] =~ /(..)/g);
    my $bzeros = length($1) if join('', @in) =~ /^(0*)/;
    my @out;
    my $size = 2 * scalar @in;
    for my $c (@in) {
        for (my $j = $size; $j--; ) {
            $c += 256 * ($out[$j] // 0);
            $out[$j] = $c % 58;
            $c /= 58;
        }
    }
    my $out = join('', map { $reverseb58{$_} } @out);
    return $1 if $out =~ /(1{$bzeros}[^1].*)/;
    return $1 if $out =~ /(1{$bzeros})/;
    die "Invalid base58!\n";
}


sub decode_base58 {
    use integer;
    my @out;
    my $azeros = length($1) if $_[0] =~ /^(1*)/;
    for my $c ( map { $b58{$_} } $_[0] =~ /./g ) {
	die("Invalid character!\n") unless defined $c;
        for (my $j = length($_[0]); $j--; ) {
            $c += 58 * ($out[$j] // 0);
            $out[$j] = $c % 256;
            $c /= 256;
        }
    }
    shift @out while @out && $out[0] == 0;
    unshift(@out, (0) x $azeros);
    return sprintf('%02x' x @out, @out);
}

sub openssl_priv_to_pub {
	my ($privkey) = @_;

	my $privkey_hex = decode_base58($privkey);

	my $key_type = "04"; # hex
	my $length = "20"; # hex

	my $asn1 = <<"__ASN1__";
asn1=SEQUENCE:private_key

[private_key]
version=INTEGER:0
included=SEQUENCE:key_info
raw=FORMAT:HEX,OCTETSTRING:${key_type}${length}${privkey_hex}

[key_info]
type=OBJECT:ED25519

__ASN1__

	my $output = `echo "${asn1}" | openssl asn1parse -i -genconf - -out - | openssl pkey -in - -inform der -noout -text_pub`;

	# remove colons
	my $pubkey = '';
	$pubkey .= $1 while $output =~ m/([0-9a-f]{2})(?::|$)/g;

	return encode_base58($pubkey);
}

sub pubkey_to_address {
	my ($pubkey) = @_;

	my $pubkey_hex = decode_base58($pubkey);
	my $pubkey_raw = pack('H*', $pubkey_hex);

	my $pkh_hex = Crypt::RIPEMD160->hexhash(sha256($pubkey_raw));
	$pkh_hex =~ tr/ //ds;

	my $version = '3a'; # hex

	my $raw = pack('H*', $version . $pkh_hex);
	my $chksum = substr(sha256_hex(sha256($raw)), 0, 8);

	return encode_base58($version . $pkh_hex . $chksum);
}
