<?php

require_once('../includes/db.php');

if ($_SERVER['REQUEST_METHOD'] == "POST")
{
	$sql = "INSERT INTO GameTable
		(url,map,created,last_updated,status)
		VALUES (?,?,NOW(),NOW(),'A')";
	$stmt = mysqli_prepare($database, $sql)
		or die("MySQL error: " . $database->error);
	$stmt->bind_param("ss", $_POST['url'], $_POST['map']);
	$stmt->execute()
		or die("MySQL error: " . $stmt->error);

	$id = mysqli_insert_id($database);
	$url = "gametable.php?id=$id";

	header("Content-Type: text/plain");
	echo "ok\n";
	echo "$url\n";
}
else
{
	die("Wrong request method");
}
