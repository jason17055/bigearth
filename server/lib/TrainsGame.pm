package TrainsGame;
use strict;
use warnings;
use JSON;
use Time::HiRes "time";

sub new
{
	my $class = shift;
	my $self = {
		rails => {},
		};
	return bless $self, $class;
}

sub load_map
{
	my $self = shift;
	my ($map_name) = @_;

	my $mapdir = "../html/maps";
	my $file = "$mapdir/$map_name.txt";
	open my $fh, "<", $file
		or die "$file: $!\n";
	my $data;
	{
		local $/;
		$data = <$fh>;
	}
	close $fh
		or die "$file: $!\n";

	$self->{map_name} = $map_name;
	$self->{map} = decode_json($data);
	return;
}

sub handle_join_request
{
	my $self = shift;
	my ($req) = @_;

	my $path = $req->uri;
	my $sid = uri_unescape($path =~ /sid=([^&;]*)$/ and $1);
	
	my $resp = HTTP::Response->new("303");
	$resp->header("Set-Cookie", "sid=$sid");
	$resp->header("Location", "/");
	return $resp;
}

sub handle_gamestate_request
{
	my $self = shift;
	my ($req) = @_;

	my $stat_struct = {
		serverTime => int((time - $main::server_start_time) * 1000),
		startTime => 120000,
		rails => $self->{rails},
		map => $self->{map},
		};

	my $resp = HTTP::Response->new("200", "OK");
	$resp->header("Content-Type", "text/json");
	my $content = encode_json($stat_struct);
	$resp->content($content);
	return $resp;
}

1;
