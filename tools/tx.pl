#!/usr/bin/env perl

use JSON;
use warnings;
use strict;

use Getopt::Std;
use File::Basename;

our %opt;
getopts('dpst', \%opt);

my $proc = basename($0);

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

our $BASE_URL = $ENV{BASE_URL} || $opt{t} ? 'http://localhost:62391' : 'http://localhost:12391';
our $DEFAULT_FEE = 0.001;

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
		required => [qw(groupId member)],
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
		required => [qw(newName newData)],
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
	# Cross-chain trading
	build_trade => {
		url => 'crosschain/build',
		required => [qw(initialQortAmount finalQortAmount fundingQortAmount secretHash bitcoinAmount)],
		optional => [qw(tradeTimeout)],
		key_name => 'creatorPublicKey',
		defaults => { tradeTimeout => 10800 },
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

my $account = account($priv_key);
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
	my $signed = sign($account->{private}, $raw);
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
	my ($creator) = @_;

	my $account = { private => $creator };
	$account->{public} = api('utils/publickey', $creator);
	$account->{address} = api('addresses/convert/{publickey}', '', '{publickey}', $account->{public});

	return $account;
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
		$curl .= " --header 'Content-Type: application/json' --data-binary '$postdata'";
		$method = 'POST';
	}
	my $response = `$curl 2>/dev/null`; 
	chomp $response;

	if ($response eq '' || substr($response, 0, 6) eq '<html>' || $response =~ m/(^\{|,)"error":(\d+)[,}]/) {
		die("API call '$method $BASE_URL/$endpoint' failed:\n$response\n");
	}

	return $response;
}
