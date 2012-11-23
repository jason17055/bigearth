package bigearth;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class LeadersFrame extends JFrame
{
	WorldMaster world;

	public LeadersFrame(WorldMaster world, Frame parent)
	{
		super("Leaders");
		this.world = world;

		String [] columnNames = {
			"Name"
			};
		Object [][] data = {
			{ "Foo" },
			{ "Bar" }};
		JTable table = new JTable(data, columnNames);
		JScrollPane scrollPane = new JScrollPane(table);
		table.setFillsViewportHeight(true);

		getContentPane().add(scrollPane);

		JPanel buttonPane = new JPanel();
		getContentPane().add(buttonPane, BorderLayout.SOUTH);

		JButton btn = new JButton("New...");
		btn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onNewClicked();
			}});
		buttonPane.add(btn);

		btn = new JButton("Delete");
		btn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onDeleteClicked();
			}});
		buttonPane.add(btn);

		btn = new JButton("Close");
		btn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				dispose();
			}});
		buttonPane.add(btn);

		pack();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setLocationRelativeTo(parent);
	}

	void onNewClicked()
	{
		try
		{

		JTextField nameField = new JTextField();
		JComponent [] inputs = new JComponent[] {
			new JLabel("Name"),
			nameField
			};

		int rv = JOptionPane.showOptionDialog(this, inputs,
			"New Leader",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE, null, null, null);
		if (rv != JOptionPane.OK_OPTION)
			return;

		if (nameField.getText().length() == 0)
			throw new Exception("You must enter a name.");

		world.newLeader(nameField.getText());

		}
		catch (Exception e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(),
				"Error",
				JOptionPane.ERROR_MESSAGE);
		}
	}

	void onDeleteClicked()
	{
		try
		{

		throw new Exception("not implemented");

		}
		catch (Exception e)
		{
			JOptionPane.showMessageDialog(this, e,
				"Error",
				JOptionPane.ERROR_MESSAGE);
		}
	}
}
