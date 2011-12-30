<?php

require_once("includes/auth.php");

if ($_SERVER['REQUEST_METHOD'] == "POST")
{
	$sql = "INSERT INTO User
		(uid,display_name,password,created)
		VALUES (?,?,SHA1(?),NOW())";
	$stmt = mysqli_prepare($database, $sql)
		or die("MySQL error: " . $database->error);
	$stmt->bind_param("sss", $_POST['email'], $_POST['display_name'], $_POST['password']);
	$stmt->execute()
		or die("MySQL error: " . $stmt->error);

	header("Location: login.php");
	exit();
}

?><!DOCTYPE HTML>
<html>
<body>
<h1>Register</h1>
<form method="post" action="<?php echo htmlspecialchars($_SERVER['REQUEST_URI'])?>">
<table>
<tr>
<td><label for="display_name_entry">Display Name:</label></td>
<td><input type="text" id="display_name_entry" name="display_name"></td>
</tr>
<tr>
<td><label for="email_entry">Email Address:</label></td>
<td><input type="text" id="email_entry" name="email"></td>
</tr>
<tr>
<td><label for="pass1">Desired Password:</label></td>
<td><input type="password" id="pass1" name="password"></td>
</tr>
</table>
<p>
<button type="submit">Register</button>
</p>
</form>
</body>
</html>
