<?php

session_name("TRAINSSESSID");
session_set_cookie_params(86400);
session_start();

require_once('includes/db.php');

function require_auth()
{
	if (!$_SESSION['uid'])
		return new_guest();

	$last_db_update = $_SESSION['last_refresh'];
	if (time() - $last_db_update > 180)
	{
		global $database;

		$sid = session_id();
		$sql = "UPDATE Session SET last_accessed=NOW()
			WHERE sid=?";
		$stmt = mysqli_prepare($database, $sql)
			or die("MySQL error: " . $database->error);
		$stmt->bind_param("s", $sid);
		$stmt->execute()
			or die("MySQL error: " . $stmt->error);

		$_SESSION['last_refresh'] = time();
	}

	return true;
}

function new_guest()
{
	global $database;

	$sql = "INSERT INTO Guest (ip_addr)
		VALUES (?)";
	$stmt = mysqli_prepare($database, $sql)
		or die("MySQL error: " . $database->error);
	$stmt->bind_param("s", $_SERVER['REMOTE_ADDR']);
	$stmt->execute()
		or die("MySQL error: " . $stmt->error);

	$id = mysqli_insert_id($database);
	$uid = "guest$id";
	$display_name = "Guest$id";

	$sql = "INSERT INTO User (uid,display_name,created)
		VALUES (?,?,NOW())";
	$stmt = mysqli_prepare($database, $sql)
		or die("MySQL error: " . $database->error);
	$stmt->bind_param("ss", $uid, $display_name);
	$stmt->execute()
		or die("MySQL error: " . $stmt->error);

	$sid = session_id();
	$sql = "INSERT INTO Session (sid,user_uid,started,last_accessed)
		SELECT ?,uid,created,created
		FROM User WHERE uid=?";
	$stmt = mysqli_prepare($database, $sql)
		or die("MySQL error: " . $database->error);
	$stmt->bind_param("ss", $sid, $uid);
	$stmt->execute()
		or die("MySQL error: " . $stmt->error);

	$_SESSION['uid'] = $uid;
	$_SESSION['last_refresh'] = time();

	return true;
}
