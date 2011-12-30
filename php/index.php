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

$sql = "SELECT url, map
	FROM GameTable
	WHERE status='A'
	ORDER BY created DESC";
$result = mysqli_query($database, $sql);
while ($row = mysqli_fetch_assoc($result))
{
	$join_url = $row['url'] . "/join?sid=" . urlencode(session_id());
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
