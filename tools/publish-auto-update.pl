#!/usr/bin/env perl

use strict;
use warnings;
use POSIX;
use Getopt::Std;

sub usage() {
	die("usage: $0 [-p api-port] dev-private-key [short-commit-hash]\n");
}

my %opt;
getopts('p:', \%opt);

usage() if @ARGV < 1 || @ARGV > 2;

my $port = $opt{p} || 12391;
my $privkey = shift @ARGV;
my $commit_hash = shift @ARGV;

my $git_dir = `git rev-parse --show-toplevel`;
die("Cannot determine git top level dir\n") unless $git_dir;

chomp $git_dir;
chdir($git_dir) || die("Can't change directory to $git_dir: $!\n");

open(POM, '<', 'pom.xml') || die ("Can't open 'pom.xml': $!\n");
my $project;
while (<POM>) {
	if (m/<artifactId>(\w+)<.artifactId>/o) {
		$project = $1;
		last;
	}
}
close(POM);

# Do we need to determine commit hash?
unless ($commit_hash) {
	# determine git branch
	my $branch_name = ` git symbolic-ref -q HEAD `;
	chomp $branch_name;
	$branch_name =~ s|^refs/heads/||; # ${branch_name##refs/heads/}

	# short-form commit hash on base branch (non-auto-update)
	$commit_hash ||= `git show --no-patch --format=%h`;
	die("Can't find commit hash\n") if ! defined $commit_hash;
	chomp $commit_hash;
	printf "Commit hash on '%s' branch: %s\n", $branch_name, $commit_hash;
} else {
	printf "Using given commit hash: %s\n", $commit_hash;
}

# build timestamp / commit timestamp on base branch
my $timestamp = `git show --no-patch --format=%ct ${commit_hash}`;
die("Can't determine commit timestamp\n") if ! defined $timestamp;
$timestamp *= 1000; # Convert to milliseconds

# locate sha256 utility
my $SHA256 = `which sha256sum || which sha256`;
chomp $SHA256;
die("Can't find sha256sum or sha256\n") unless length($SHA256) > 0;

# SHA256 of actual update file
my $sha256 = `git show auto-update-${commit_hash}:${project}.update | ${SHA256} | head -c 64`;
die("Can't calculate SHA256 of ${project}.update\n") unless $sha256 =~ m/(\S{64})/;
chomp $sha256;

# long-form commit hash of HEAD on auto-update branch
my $update_hash = `git rev-parse refs/heads/auto-update-${commit_hash}`;
die("Can't find commit hash for HEAD on auto-update-${commit_hash} branch\n") if ! defined $update_hash;
chomp $update_hash;

printf "Build timestamp (ms): %d / 0x%016x\n", $timestamp, $timestamp;
printf "Auto-update commit hash: %s\n", $update_hash;
printf "SHA256 of ${project}.update: %s\n", $sha256;

my $tx_type = 10;
my $tx_timestamp = time() * 1000;
my $tx_group_id = 1;
my $service = 1;
printf "\nARBITRARY(%d) transaction with timestamp %d, txGroupID %d and service %d\n", $tx_type, $tx_timestamp, $tx_group_id, $service;

my $data_hex = sprintf "%016x%s%s", $timestamp, $update_hash, $sha256;
printf "\nARBITRARY transaction data payload: %s\n", $data_hex;

my $n_payments = 0;
my $is_raw = 1; # RAW_DATA
my $data_length = length($data_hex) / 2; # two hex chars per byte
my $fee = 0.001 * 1e8;

die("Something's wrong: data length is not 60 bytes!\n") if $data_length != 60;

my $pubkey = `curl --silent --url http://localhost:${port}/utils/publickey --data ${privkey}`;
die("Can't convert private key to public key:\n$pubkey\n") unless $pubkey =~ m/^\w{44}$/;
printf "\nPublic key: %s\n", $pubkey;

my $pubkey_hex = `curl --silent --url http://localhost:${port}/utils/frombase58 --data ${pubkey}`;
die("Can't convert base58 public key to hex:\n$pubkey_hex\n") unless $pubkey_hex =~ m/^[A-Za-z0-9]{64}$/;
printf "Public key hex: %s\n", $pubkey_hex;

my $address = `curl --silent --url http://localhost:${port}/addresses/convert/${pubkey}`;
die("Can't convert base58 public key to address:\n$address\n") unless $address =~ m/^\w{33,34}$/;
printf "Address: %s\n", $address;

my $reference = `curl --silent --url http://localhost:${port}/addresses/lastreference/${address}`;
die("Can't fetch last reference for $address:\n$reference\n") unless $reference =~ m/^\w{87,88}$/;
printf "Last reference: %s\n", $reference;

my $reference_hex = `curl --silent --url http://localhost:${port}/utils/frombase58 --data ${reference}`;
die("Can't convert base58 reference to hex:\n$reference_hex\n") unless $reference_hex =~ m/^[A-Za-z0-9]{128}$/;
printf "Last reference hex: %s\n", $reference_hex;

my $raw_tx_hex = sprintf("%08x%016x%08x%s%s%08x%08x%02x%08x%s%016x", $tx_type, $tx_timestamp, $tx_group_id, $reference_hex, $pubkey_hex, $n_payments, $service, $is_raw, $data_length, $data_hex, $fee);
printf "\nRaw transaction hex:\n%s\n", $raw_tx_hex;

my $raw_tx = `curl --silent --url http://localhost:${port}/utils/tobase58/${raw_tx_hex}`;
die("Can't convert raw transaction hex to base58:\n$raw_tx\n") unless $raw_tx =~ m/^\w{255,265}$/; # Roughly 255 to 265 base58 chars
printf "\nRaw transaction (base58):\n%s\n", $raw_tx;

my $sign_data = qq|' { "privateKey": "${privkey}", "transactionBytes": "${raw_tx}" } '|;
my $signed_tx = `curl --silent -H "accept: text/plain" -H "Content-Type: application/json" --url http://localhost:${port}/transactions/sign --data ${sign_data}`;
die("Can't sign raw transaction:\n$signed_tx\n") unless $signed_tx =~ m/^\w{345,355}$/; # +90ish longer than $raw_tx
printf "\nSigned transaction:\n%s\n", $signed_tx;

# Check we can actually fetch update
my $origin = `git remote get-url origin`;
die("Unable to get github url for 'origin'?\n") unless $origin && $origin =~ m/:(.*)\.git$/;
my $repo = $1;
my $update_url = "https://github.com/${repo}/raw/${update_hash}/${project}.update";

my $fetch_result = `curl --silent -o /dev/null --location --range 0-1 --head --write-out '%{http_code}' --url ${update_url}`;
die("\nUnable to fetch update from ${update_url}\n") if $fetch_result ne '200';
printf "\nUpdate fetchable from ${update_url}\n";

# Flush STDOUT after every output
$| = 1;
print "\n";
for (my $delay = 5; $delay > 0; --$delay) {
	printf "\rSubmitting transaction in %d second%s... CTRL-C to abort ", $delay, ($delay != 1 ? 's' : '');
	sleep 1;
}

printf "\rSubmitting transaction NOW...                                    \n";
my $result = `curl --silent --url http://localhost:${port}/transactions/process --data ${signed_tx}`;
chomp $result;
die("Transaction wasn't accepted:\n$result\n") unless $result eq 'true';

my $decoded_tx = `curl --silent -H "Content-Type: application/json" --url http://localhost:${port}/transactions/decode --data ${signed_tx}`;
printf "\nTransaction accepted:\n$decoded_tx\n";
