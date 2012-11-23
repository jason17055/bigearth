package bigearth;

import java.util.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;

public class LeadersFrame extends JFrame
{
	WorldMaster world;
	JTable leadersTable;

	static final String [] COLUMN_NAMES = { "Name" };

	private void reloadTable()
	{
		String [] leaderNames = world.leaders.keySet().toArray(new String[0]);
		Object [][] data = new Object[leaderNames.length][1];
		for (int i = 0; i < leaderNames.length; i++)
		{
			data[i][0] = world.leaders.get(leaderNames[i]).displayName;
		}
		DefaultTableModel model = new DefaultTableModel(data, COLUMN_NAMES);
		leadersTable.setModel(model);
	}

	public LeadersFrame(WorldMaster world, Frame parent)
	{
		super("Leaders");
		this.world = world;

		Object [][] data = {};
		leadersTable = new JTable(data, COLUMN_NAMES);
		JScrollPane scrollPane = new JScrollPane(leadersTable);
		leadersTable.setFillsViewportHeight(true);
		reloadTable();

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
		reloadTable();

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
