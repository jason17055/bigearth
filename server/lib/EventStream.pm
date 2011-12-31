package EventStream;
use strict;
use warnings;
use JSON;

my %queued_events_by_sid;
my %waiting_event_listeners_by_sid;

sub new
{
	my $class = shift;
	return bless {
		waiting_listeners => [],
		sent_events => [],
		}, $class;
}

sub handle_event_request
{
	my $self = shift;
	my ($req, $http) = @_;

	my $path = $req->uri;
	my $req_id = ($path =~ m{^/event/(\d+)} and $1) || $self->next_id;

	if (my $evt = $self->{sent_events}->[$req_id])
	{
		send_event($evt, $http);
		return;
	}
	else
	{
		# must wait
		$self->{waiting_listeners} ||= [];
		push @{$self->{waiting_listeners}}, $http;
	}
	return;
}

sub post_event
{
	my $self = shift;
	my ($evt) = @_;

	$evt->{id} = $self->next_id;
	$evt->{nextEvent} = $evt->{id} + 1;
	push @{$self->{sent_events}}, $evt;

	if (my $wl = delete $self->{waiting_listeners})
	{
		foreach my $http (@$wl)
		{
			send_event($evt, $http);
		}
	}

	return $evt;
}

sub send_event
{
	my ($evt, $http) = @_;

	my $resp = HTTP::Response->new("200","OK");
	$resp->header("Content-Type", "text/json");
	my $content = encode_json($evt);
	$resp->content($content);
	$http->write_message($resp);
	return;
}

sub next_id
{
	my $self = shift;
	my $len = @{$self->{sent_events}};
	return $len;
}

1;
