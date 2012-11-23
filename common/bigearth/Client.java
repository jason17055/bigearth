package bigearth;

import javax.swing.*;

public class Client
{
	public static void main(String [] args)
	{
		JTextField hostField = new JTextField();
		hostField.setText("localhost");

		JTextField userField = new JTextField();
		JPasswordField passField = new JPasswordField();

		JComponent [] inputs = new JComponent[] {
			new JLabel("Hostname"),
			hostField,
			new JLabel("Username"),
			userField,
			new JLabel("Password"),
			passField
			};
		int rv = JOptionPane.showOptionDialog(null, inputs,
			"Login",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE, null, null, null);
		if (rv != JOptionPane.OK_OPTION)
			return;


	}
}
