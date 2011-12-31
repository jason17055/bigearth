package TrainsGame;
use strict;
use warnings;
use JSON;

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

1;
