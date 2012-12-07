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
		inventoryTable.setFillsViewportHeight(true);
		reloadTable();

		mainPane.add(scrollPane);

		JPanel buttonPane = new JPanel();
		getContentPane().add(buttonPane, BorderLayout.SOUTH);

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
}
