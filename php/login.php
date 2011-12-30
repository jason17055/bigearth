<?php

require_once("includes/auth.php");
require_once("includes/db.php");

if ($_SERVER['REQUEST_METHOD'] == "POST")
{
	$sql = "SELECT 1 FROM User
		WHERE uid=? AND password=SHA1(?)";
	$stmt = mysqli_prepare($database, $sql)
		or die("MySQL error: " . $database->error);
	$stmt->bind_param("ss", $_POST['uid'], $_POST['password']);
	$stmt->execute()
		or die("MySQL error: " . $stmt->error);

	if ($stmt->num_rows == 1)
	{
		$sql = "DELETE FROM Session
			WHERE sid=?";
		$stmt = mysqli_prepare($database, $sql);
		$stmt->bind_param("s", session_id());
		$stmt->execute();

		$_SESSION['uid'] = $_POST['uid'];
		$_SESSION['last_refresh'] = time();

		$sql = "INSERT INTO Session
			(sid,user_uid,started,last_accessed)
			VALUES (?,?,NOW(),NOW())
			";
		$stmt = mysqli_prepare($database, $sql)
			or die("MySQL error: " . $database->error);
		$stmt->bind_param("ss", session_id(), $_SESSION['uid']);
		$stmt->execute()
			or die("MySQL error: " . $stmt->error);

		header("Location: .");
		exit();
	}
	else
	{
		$message = "Wrong username or password!";
	}
}

?><!DOCTYPE HTML>
<html>
<body>
<h1>Login</h1>
<?php echo $message?>
<form method="post" action="<?php echo htmlspecialchars($_SERVER['REQUEST_URI'])?>">
<table>
<tr>
<td><label for="uid_entry">Email address:</label></td>
<td><input type="text" id="uid_entry" name="uid"></td>
</tr>
<tr>
<td><label for="password_entry">Password:</label></td>
<td><input type="password" id="password_entry" name="password"></td>
</tr>
</table>
<p>
<button type="submit">Login</button>
</p>
</form>
</body>
</html>
