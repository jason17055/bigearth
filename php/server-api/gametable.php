<?php

require_once('../includes/db.php');

if ($_SERVER['REQUEST_METHOD'] == "POST")
{
	$sql = "UPDATE GameTable
		SET last_updated=NOW(),
			status=?
		WHERE id=?
		";
	$stmt = mysqli_prepare($database, $sql)
		or die("MySQL error: " . $database->error);
	$stmt->bind_param("ss", $_POST['status'], $_REQUEST['id']);
	$stmt->execute()
		or die("MySQL error: " . $stmt->error);

	header("Content-Type: text/plain");
	echo "ok\n";
}
else
{
	die("Wrong request method");
}
