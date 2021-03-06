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
	Location mobLocation;
	MyListener listner;

	JTable inventoryTable;
	CommodityBagTableModel inventoryModel;
	JTable availableTable;
	CommodityBagTableModel availableModel;
	JButton closeBtn;

	InventoryDialog(Window owner, Client client, String mobName)
	{
		super(owner, "Inventory", Dialog.ModalityType.APPLICATION_MODAL);
		this.client = client;
		this.mobName = mobName;
		this.listner = new MyListener();

		this.client.map.addListener(listner);
		this.client.mobs.addListener(listner);

		JPanel mainPane = new JPanel(new GridBagLayout());
		getContentPane().add(mainPane, BorderLayout.CENTER);

		inventoryModel = new CommodityBagTableModel();
		inventoryTable = new JTable(inventoryModel);
		JScrollPane scrollPane = new JScrollPane(inventoryTable);
		inventoryTable.setFillsViewportHeight(false);
		inventoryTable.setPreferredScrollableViewportSize(
			new Dimension(200,200));
		reloadMobInventory();

		GridBagConstraints c1 = new GridBagConstraints();
		c1.gridx = 0;
		c1.gridy = 0;
		mainPane.add(new JLabel("Carrying/Herding"), c1);
		c1.gridy++;
		mainPane.add(scrollPane, c1);

		availableModel = new CommodityBagTableModel();
		availableTable = new JTable(availableModel);
		JScrollPane scrollPane2 = new JScrollPane(availableTable);
		availableTable.setFillsViewportHeight(false);
		availableTable.setPreferredScrollableViewportSize(
			new Dimension(200,200));
		reloadRegionStock();

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
		reloadMobInventory();
		reloadRegionStock();
	}

	static final String [] COLUMN_NAMES = { "Commodity", "Amount" };
	private void reloadMobInventory()
	{
		MobListModel mobList = client.mobs;
		MobInfo mob = mobList.mobs.get(mobName);
		mobLocation = mob.location;

		inventoryModel.refreshFrom(mob.stock);
	}

	private void reloadRegionStock()
	{
		MobListModel mobList = client.mobs;
		MobInfo mob = mobList.mobs.get(mobName);
		MapModel map = client.map;
		RegionProfile region = map.getRegion(mob.location);

		if (region.hasStock())
		{
			availableModel.refreshFrom(region.stock);
			availableTable.setVisible(true);
		}
		else
		{
			availableTable.setVisible(false);
		}
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

		try {

		CommodityType ct = (CommodityType) availableTable.getValueAt(row, 0);
		client.takeCommodity(mobName, ct, 1);

		}catch (Exception e)
		{
			JOptionPane.showMessageDialog(this,
				e,
				"Error",
				JOptionPane.ERROR_MESSAGE);
			return;
		}
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

	private class MyListener
		implements MapModel.Listener, MobListModel.Listener
	{
		//implements MapModel.Listener
		public void regionUpdated(Location loc)
		{
			if (!loc.equals(mobLocation))
				return; //not interested

			SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				reloadRegionStock();
			}
			});
		}

		//implements MobListModel.Listener
		public void mobRemoved(String mobName, MobInfo.RemovalDisposition disposition)
		{
			if (!mobName.equals(InventoryDialog.this.mobName))
				return; //not interested

			SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				dispose();
			}
			});
		}

		//implements MobListModel.Listener
		public void mobUpdated(String mobName)
		{
			if (!mobName.equals(InventoryDialog.this.mobName))
				return; //not interested

			SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				reloadMobInventory();
			}
			});
		}
	}
}
