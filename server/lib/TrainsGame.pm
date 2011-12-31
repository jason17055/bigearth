package TrainsGame;
use strict;
use warnings;
use JSON;
use Time::HiRes "time";
use Sys::Syslog;
use URI::Escape;

sub new
{
	my $class = shift;
	my $self = {
		rails => {},
		players => {},
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
	$self->{geometry} = Geometry1->new(length($self->{map}->{terrain}->[0]), scalar(@{$self->{map}->{terrain}}));

	$self->auto_create_demands();

	return;
}

sub auto_create_demands
{
	my $self = shift;

	my %all_resource_types;
	foreach my $city_id (keys %{$self->{map}->{cities}})
	{
		my $c = $self->{map}->{cities}->{$city_id};
		foreach my $resource (@{$c->{offers}})
		{
			$all_resource_types{$resource} ||= {};
			$all_resource_types{$resource}->{$city_id} = 1;
		}
	}

	my @all_demands;
	foreach my $city_id (keys %{$self->{map}->{cities}})
	{
		my $c = $self->{map}->{cities}->{$city_id};
		foreach my $resource (keys %all_resource_types)
		{
			my $r = $all_resource_types{$resource};
			next if $r->{$city_id};

			my $best = 9 ** 9;
			foreach my $src (keys %$r)
			{
				my $d = $self->{geometry}->simple_distance($city_id, $src);
				if ($d < $best)
				{
					$best = $d;
				}
			}

			my $value = int(($best * 0.5)+0.5);
			if ($value >= 1)
			{
				push @all_demands, [ $city_id, $resource, $value ];
			}
		}
	}

	shuffle_array(\@all_demands);
	$self->{future_demands} = \@all_demands;
}

sub shuffle_array
{
	my ($a) = @_;
	my $l = @$a;
	for (my $i = 0; $i < $l; $i++)
	{
		my $j = $i + int(rand($l - $i));
		my $t = $a->[$i];
		$a->[$i] = $a->[$j];
		$a->[$j] = $t;
	}
	return $a;
}

sub new_player
{
	my $self = shift;
	my ($pid) = @_;

	$self->{players}->{$pid} = {
		money => 50,
		demands => [ splice @{$self->{future_demands}}, 0, 5 ],
		};
}

sub my_unescape
{
	my $x = shift;
	$x =~ s/\+/ /gs;
	return uri_unescape($x);
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
		nextEvent => $self->{events}->next_id,
		rails => $self->{rails},
		map => $self->{map},
		players => {},
		};
	foreach my $pid (keys %{$self->{players}})
	{
		my $p = $self->{players}->{$pid};
		$stat_struct->{players}->{$pid} = {
			money => $p->{money},
			demands => $p->{demands},
			};
	}

	my $resp = HTTP::Response->new("200", "OK");
	$resp->header("Content-Type", "text/json");
	my $content = encode_json($stat_struct);
	$resp->content($content);
	return $resp;
}

sub handle_request_request
{
	my $self = shift;
	my ($req) = @_;

	my $path = $req->uri;
	my $verb = ($path =~ m{^/request/(.*)$}s and $1)
		or return;

	my $m = "handle_${verb}_action";
	if (!$self->can($m))
	{
		syslog "warning", "verb %s not found", $verb;
		my $resp = HTTP::Response->new("404", "Not found");
		$resp->content("verb $verb not found\n");
		return $resp;
	}

	my @data = map {
		my ($k, $v) = split /=/, $_;
		my_unescape($k) => my_unescape($v)
		}
		split /&/, $req->content;

	my $answer = $self->$m(\@data, $req) || {};

	my $resp = HTTP::Response->new("200", "OK");
	$resp->header("Content-Type", "text/json");
	my $content = encode_json($answer);
	$resp->content($content);
	return $resp;
}

sub handle_build_action
{
	my $self = shift;
	my ($args_arrayref) = @_;
	my %args = @$args_arrayref;

	my $pid = $args{player} || 1;
	my $p = $self->{players}->{$pid}
		or return;

	syslog "info", "player %s is building %d worth of tracks",
			$pid, $args{cost};

	my %changes;
	foreach my $track_idx (split /\s+/, $args{rails})
	{
		if (!$self->{rails}->{$track_idx}
			|| $self->{rails}->{$track_idx} ne $pid)
		{
			$self->{rails}->{$track_idx} = $pid;
			$changes{$track_idx} = $pid;
		}
	}

	$p->{money} -= $args{cost};
	$self->{events}->post_event(
		{
		event => "track-built",
		rails => \%changes,
		playerMoney => { $pid => $p->{money} },
		}
		);

	return;
}

sub handle_editMap_action
{
	my $self = shift;
	my ($args_arrayref) = @_;

	my $map = {
		cities => {},
		};
	for (my $i = 0; $i < @$args_arrayref; $i += 2)
	{
		my $k = $args_arrayref->[$i];
		my $v = $args_arrayref->[$i + 1];

		if ($k eq "terrain")
		{
			my @terrain = split /\n/, $v;
			$map->{terrain} = \@terrain;
		}
		elsif ($k eq "rivers")
		{
			my %tmp = map { $_ => 1 } split / /, $v;
			$map->{rivers} = \%tmp;
		}
		elsif ($k =~ m{^cities\[(\d+)\]\[name\]$})
		{
			$map->{cities}->{$1} ||= {};
			$map->{cities}->{$1}->{name} = $v;
		}
		elsif ($k =~ m{^cities\[(\d+)\]\[offers\]\[\]$})
		{
			$map->{cities}->{$1} ||= {};
			$map->{cities}->{$1}->{offers} ||= [];
			push @{$map->{cities}->{$1}->{offers}}, $v;
		}
		else
		{
			print STDERR "what is $k\n";
		}
	}

	open my $fh, ">", "map.txt";
	print $fh encode_json($map);
	close $fh;

	return;
}

package Geometry1;

sub new
{
	my $class = shift;
	my ($width, $height) = @_;
	return bless { width => $width, height => $height }, $class;
}

sub simple_distance
{
	my $self = shift;
	my ($cell1, $cell2) = @_;

	my $row1 = $self->get_cell_row($cell1);
	my $col1 = $self->get_cell_column($cell1);
	my $row2 = $self->get_cell_row($cell2);
	my $col2 = $self->get_cell_column($cell2);

	my $dist_rows = abs($row2 - $row1);
	my $dist_cols = abs($col2 - $col1);
	my $diag = int($dist_rows / 2);
	return $dist_rows + ($dist_cols > $diag ? $dist_cols - $diag : 0);
}

sub get_cell_row
{
	my $self = shift;
	my ($cell_idx) = @_;

	return int($cell_idx / $self->{width});
}

sub get_cell_column
{
	my $self = shift;
	my ($cell_idx) = @_;

	return $cell_idx % $self->{width};
}

1;
