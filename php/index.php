<?php

require("includes/auth.php");
require_auth();

?><body>
<div>
You are <?php echo htmlspecialchars($_SESSION['uid'])?>.
<a href="login.php">Login</a>
<a href="register.php">Register</a>
</div>

<h2>Available Games</h2>
<table border="1">

<?php

$sql = "SELECT url, map, secret
	FROM GameTable
	WHERE status='A'
	AND created >= SUBTIME(NOW(),'12:00:00')
	ORDER BY created DESC";
$result = mysqli_query($database, $sql);
while ($row = mysqli_fetch_assoc($result))
{
	$cs = sha1($row['secret'] . "." . $_SESSION['uid']);
	$join_url = $row['url'] . "/join?id=" . urlencode($_SESSION['uid']) . "&cs=" . urlencode($cs);
	?>
<tr>
<td><?php echo htmlspecialchars($row['map'])?></td>
<td><a href="<?php echo htmlspecialchars($join_url)?>">Join</a></td>
</tr>
<?php
}

?>
</table>
</body>
</html>
