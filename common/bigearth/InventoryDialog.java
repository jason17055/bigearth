package bigearth;

import java.util.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;

public class InventoryDialog extends JDialog
{
	Client client;
	String mobName;

	JTable inventoryTable;
	JTable availableTable;
	JButton closeBtn;

	InventoryDialog(Window owner, Client client, String mobName)
	{
		super(owner, "Inventory", Dialog.ModalityType.APPLICATION_MODAL);
		this.client = client;
		this.mobName = mobName;

		JPanel mainPane = new JPanel(new GridBagLayout());
		getContentPane().add(mainPane, BorderLayout.CENTER);

		inventoryTable = new JTable();
		JScrollPane scrollPane = new JScrollPane(inventoryTable);
		inventoryTable.setFillsViewportHeight(false);
		inventoryTable.setPreferredScrollableViewportSize(
			new Dimension(200,200));
		reloadTable();

		GridBagConstraints c1 = new GridBagConstraints();
		c1.gridx = 0;
		c1.gridy = 0;
		mainPane.add(new JLabel("Carrying/Herding"), c1);
		c1.gridy++;
		mainPane.add(scrollPane, c1);

		availableTable = new JTable();
		JScrollPane scrollPane2 = new JScrollPane(availableTable);
		availableTable.setFillsViewportHeight(false);
		availableTable.setPreferredScrollableViewportSize(
			new Dimension(200,200));
		reloadTable2();

		JPanel middleButtonsPane = new JPanel();
		middleButtonsPane.setLayout(new BoxLayout(middleButtonsPane, BoxLayout.PAGE_AXIS));
		GridBagConstraints c3 = new GridBagConstraints();
		c3.gridx = 1;
		c3.gridy = 0;
		c3.gridheight = 2;
		mainPane.add(middleButtonsPane, c3);

		JButton takeBtn = new JButton("<- Take");
		takeBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onTakeClicked();
			}});
		middleButtonsPane.add(takeBtn);

		JButton dropBtn = new JButton("Drop ->");
		dropBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onDropClicked();
			}});
		middleButtonsPane.add(dropBtn);
	
		GridBagConstraints c2 = new GridBagConstraints();
		c2.gridx = 2;
		c2.gridy = 0;
		mainPane.add(new JLabel("At this Location"), c2);
		c2.gridy++;
		mainPane.add(scrollPane2, c2);

		JPanel buttonPane = new JPanel();
		getContentPane().add(buttonPane, BorderLayout.SOUTH);

		JButton refreshBtn = new JButton("Refresh");
		refreshBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onRefreshClicked();
			}});
		buttonPane.add(refreshBtn);

		closeBtn = new JButton("Close");
		closeBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				InventoryDialog.this.dispose();
			}});
		buttonPane.add(closeBtn);

		pack();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setLocationRelativeTo(owner);
	}

	private void onRefreshClicked()
	{
		reloadTable();
		reloadTable2();
	}

	static final String [] COLUMN_NAMES = { "Commodity", "Amount" };
	private void reloadTable()
	{
		MobListModel mobList = client.mobs;
		MobInfo mob = mobList.mobs.get(mobName);

		CommodityType [] commodities = mob.stock.keySet().toArray(new CommodityType[0]);
		Object [][] data = new Object[commodities.length][2];
		for (int i = 0; i < commodities.length; i++)
		{
			data[i][0] = commodities[i];
			data[i][1] = new Long(mob.getStock(commodities[i]));
		}

		DefaultTableModel model = new DefaultTableModel(data, COLUMN_NAMES);
		inventoryTable.setModel(model);
	}

	private void reloadTable2()
	{
		Object [][] data = new Object[1][2];
		data[0][0] = CommodityType.WOOD;
		data[0][1] = new Long(42);

		DefaultTableModel model = new DefaultTableModel(data, COLUMN_NAMES);
		availableTable.setModel(model);
	}

	private void onTakeClicked()
	{
		int row = availableTable.getSelectedRow();
		if (row == -1)
		{
			JOptionPane.showMessageDialog(this,
				"Select something from at this location.",
				"Error",
				JOptionPane.ERROR_MESSAGE);
			return;
		}

		CommodityType ct = (CommodityType) availableTable.getValueAt(row, 0);
		System.out.println("want to take "+ct);
	}

	private void onDropClicked()
	{
		int row = inventoryTable.getSelectedRow();
		if (row == -1)
		{
			JOptionPane.showMessageDialog(this,
				"Select something from inventory.",
				"Error",
				JOptionPane.ERROR_MESSAGE);
			return;
		}

		try{

		CommodityType ct = (CommodityType) inventoryTable.getValueAt(row, 0);
		client.dropCommodity(mobName, ct, 1);

		}catch (Exception e)
		{
			JOptionPane.showMessageDialog(this,
				e,
				"Error",
				JOptionPane.ERROR_MESSAGE);
			return;
		}

	}
}
