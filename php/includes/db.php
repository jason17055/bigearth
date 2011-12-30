<?php

$dbhost = '184.172.139.65';
$dbname = 'jaslong_trains';
$dbuser = 'jaslong_truser';
$dbpass = 'joGpcj325';
$database = mysqli_connect($dbhost,$dbuser,$dbpass,$dbname);
if (!$database)
{
	die("could not connect: " . mysqli_connect_error());
}


function dbi_quote($str)
{
	global $database;
	if (is_null($str))
	{
		return "NULL";
	}
	return "'" . mysqli_real_escape_string($database,$str) . "'";
}

function js_quote($str)
{
	$str = str_replace("\\", "\\\\", $str);
	$str = str_replace("'", "\\'", $str);
	$str = str_replace("\n", "\\n", $str);
	return $str;
}

?>
