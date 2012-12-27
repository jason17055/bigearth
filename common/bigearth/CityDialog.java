package bigearth;

import java.io.*;
import java.net.URL;
import java.util.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class CityDialog extends JDialog
{
	Client client;
	Location cityLocation;
	MyListener listner;

	JLabel populationLbl;
	JLabel childrenLbl;;

	JPanel landPane;
	int nextZoneItemRow = 0;
	Map<ZoneType, JLabel> zoneItemLabels = new HashMap<ZoneType, JLabel>();

	JPanel stockPane;
	int nextStockItemRow = 0;
	Map<CommodityType, JLabel> stockItemLabels = new HashMap<CommodityType, JLabel>();

	JComboBox developSelect;
	JComboBox equipSelect;
	DefaultListModel messagesListModel;
	JList messagesList;
	JLabel scientistsLbl;
	DefaultListModel scienceListModel;
	JList scienceList;

	static ZoneType [] developChoices = new ZoneType[] {
		ZoneType.MUD_COTTAGES,
		ZoneType.WOOD_COTTAGES,
		ZoneType.STONE_COTTAGES,
		ZoneType.FARM,
		ZoneType.PASTURE };
	static MobType [] equipChoices = new MobType[] {
		MobType.SETTLER
		};

	void onWindowClosed()
	{
		this.client.removeListener(this.listner);
	}

	CityDialog(Window owner, Client client, Location cityLocation)
	{
		super(owner, "City", Dialog.ModalityType.APPLICATION_MODAL);
		this.client = client;
		this.cityLocation = cityLocation;

		JTabbedPane tabbedPane = new JTabbedPane();
		getContentPane().add(tabbedPane, BorderLayout.CENTER);

		JComponent landPane = initLandPane();
		tabbedPane.addTab("Land", landPane);

		JComponent peoplePane = initPeoplePane();
		tabbedPane.addTab("People", peoplePane);

		JComponent stockPane = initStockPane();
		tabbedPane.addTab("Stock", stockPane);

		JComponent messagesPane = initMessagesPane();
		tabbedPane.addTab("Messages", messagesPane);

		JComponent sciencePane = initSciencePane();
		tabbedPane.addTab("Science", sciencePane);

		JPanel buttonPane = new JPanel();
		getContentPane().add(buttonPane, BorderLayout.SOUTH);

		JButton renameBtn = new JButton("Rename City");
		renameBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onRenameClicked();
			}});
		buttonPane.add(renameBtn);

		developSelect = new JComboBox(developChoices);
		buttonPane.add(developSelect);

		JButton developBtn = new JButton("Develop");
		developBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onDevelopClicked();
			}});
		buttonPane.add(developBtn);

		equipSelect = new JComboBox(equipChoices);
		buttonPane.add(equipSelect);

		JButton equipBtn = new JButton("Equip");
		equipBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onEquipClicked();
			}});
		buttonPane.add(equipBtn);

		JButton closeBtn = new JButton("Close");
		closeBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onCloseClicked();
			}});
		buttonPane.add(closeBtn);

		reloadCityInfo();

		pack();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setLocationRelativeTo(owner);

		addWindowListener(new WindowAdapter() {
			public void windowClosed(WindowEvent ev)
			{
				onWindowClosed();
			}
		});

		this.listner = new MyListener();
		this.client.addListener(listner);
		//this.client.mobs.addListener(listner);
	}

	private JComponent initSciencePane()
	{
		JPanel mainPane = new JPanel(new GridBagLayout());

		GridBagConstraints c1 = new GridBagConstraints();
		c1.gridy = 0;
		c1.gridx = 0;
		c1.gridwidth = 2;
		scientistsLbl = new JLabel();
		mainPane.add(scientistsLbl, c1);

		scienceListModel = new DefaultListModel();

		scienceList = new JList(scienceListModel);
		JScrollPane scienceListScroll = new JScrollPane(scienceList);
		scienceListScroll.setPreferredSize(new Dimension(250, 50));
		GridBagConstraints c3 = new GridBagConstraints();
		c3.gridy = 1;
		c3.gridx = 0;
		c3.gridwidth = 2;
		c3.weighty = c3.weightx = 1.0;
		c3.fill = GridBagConstraints.BOTH;
		mainPane.add(scienceListScroll, c3);

		return mainPane;
	}

	private JComponent initMessagesPane()
	{
		JPanel mainPane = new JPanel(new GridBagLayout());

		messagesListModel = new DefaultListModel();

		messagesList = new JList(messagesListModel);
		JScrollPane messagesListScroll = new JScrollPane(messagesList);
		messagesListScroll.setPreferredSize(new Dimension(250, 50));
		GridBagConstraints c3 = new GridBagConstraints();
		c3.gridy = 0;
		c3.gridx = 0;
		c3.gridwidth = 2;
		c3.weighty = c3.weightx = 1.0;
		c3.fill = GridBagConstraints.BOTH;
		mainPane.add(messagesListScroll, c3);

		return mainPane;
	}

	private void addStockItem(CommodityType ct)
	{
		GridBagConstraints c1 = new GridBagConstraints();
		c1.gridx = 0;
		c1.anchor = GridBagConstraints.WEST;

		GridBagConstraints c2 = new GridBagConstraints();
		c2.gridx = 1;
		c2.anchor = GridBagConstraints.WEST;
		c2.weightx = 1.0;

		GridBagConstraints c3 = new GridBagConstraints();
		c3.gridx = 2;
		c3.anchor = GridBagConstraints.EAST;

		c1.gridy = c2.gridy = c2.gridy = nextStockItemRow;
		nextStockItemRow++;

		ImageIcon stockIcon = new ImageIcon(ct.getIconFilename().toString());
		stockPane.add(new JLabel(stockIcon), c1);
		stockPane.add(new JLabel(ct.getDisplayName()), c2);

		JLabel qtyLbl = new JLabel();
		stockItemLabels.put(ct, qtyLbl);
		stockPane.add(qtyLbl, c3);
	}

	private void updateStockItem(CommodityType ct, long qty)
	{
		if (!stockItemLabels.containsKey(ct))
		{
			addStockItem(ct);
		}

		stockItemLabels.get(ct).setText(Long.toString(qty));
	}

	private JComponent initStockPane()
	{
		stockPane = new JPanel(new GridBagLayout());
		return stockPane;
	}

	private JComponent initPeoplePane()
	{
		JPanel mainPane = new JPanel(new GridBagLayout());

		GridBagConstraints c1 = new GridBagConstraints();
		c1.gridx = 0;
		c1.anchor = GridBagConstraints.FIRST_LINE_START;
		c1.weightx = 1.0;

		GridBagConstraints c2 = new GridBagConstraints();
		c2.gridx = 1;
		c2.anchor = GridBagConstraints.FIRST_LINE_END;

		c1.gridy = c2.gridy = 0;
		mainPane.add(new JLabel("Population"), c1);
		populationLbl = new JLabel();
		mainPane.add(populationLbl, c2);

		c1.gridy = ++c2.gridy;
		mainPane.add(new JLabel("Children"), c1);
		childrenLbl = new JLabel();
		mainPane.add(childrenLbl, c2);

		return mainPane;
	}

	private JComponent initLandPane()
	{
		landPane = new JPanel(new GridBagLayout());
		return landPane;
	}

	private void updateZoneItem(ZoneType zone, int quantity)
	{
		if (!zoneItemLabels.containsKey(zone))
			addZoneItem(zone);

		zoneItemLabels.get(zone).setText(Integer.toString(quantity));
	}

	private void addZoneItem(ZoneType zone)
	{
		GridBagConstraints c1 = new GridBagConstraints();
		c1.gridx = 0;
		c1.anchor = GridBagConstraints.WEST;

		GridBagConstraints c2 = new GridBagConstraints();
		c2.gridx = 1;
		c2.anchor = GridBagConstraints.WEST;
		c2.weightx = 1.0;

		GridBagConstraints c3 = new GridBagConstraints();
		c3.gridx = 2;
		c3.anchor = GridBagConstraints.EAST;

		c1.gridy = c2.gridy = c3.gridy = nextZoneItemRow;
		nextZoneItemRow++;

		URL zoneIconUrl = zone.getIconResource();
		ImageIcon zoneIcon = zoneIconUrl != null ? new ImageIcon(zoneIconUrl) : null;
		landPane.add(new JLabel(zoneIcon), c1);
		landPane.add(new JLabel(zone.getDisplayName()), c2);

		JLabel qtyLbl = new JLabel();
		zoneItemLabels.put(zone, qtyLbl);
		landPane.add(qtyLbl, c3);
	}

	void addCityMessage(String message)
	{
		messagesListModel.add(0, message);
	}

	private void reloadCityInfo()
	{
		try
		{
			CityInfo city = client.getCity(cityLocation);
			if (city == null)
				return;

			loadCityInfo(city);
		}
		catch (IOException e)
		{
			//FIXME
			e.printStackTrace(System.err);
		}
	}

	private void loadCityInfo(CityInfo city)
	{
		setTitle("City: "+city.displayName);
		populationLbl.setText(city.hasPopulation() ?
			Integer.toString(city.population) : null);
		childrenLbl.setText(city.hasChildren() ?
			Integer.toString(city.children) : null);

		assert city.hasZones();
		for (Map.Entry<ZoneType,Integer> e : city.zones.entrySet())
		{
			ZoneType zone = e.getKey();
			int quantity = e.getValue();

			if (zone != ZoneType.NATURAL)
				updateZoneItem(zone, quantity);
		}

		assert city.hasStock();
		for (CommodityType ct : city.stock.getCommodityTypesArray())
		{
			updateStockItem(ct, city.stock.getQuantity(ct));
		}

		scientistsLbl.setText(city.hasScientists() ?
			"This city has "+city.scientists+" scientists" : null);
		scienceListModel = new DefaultListModel();
		if (city.hasPartialScience())
		{
			for (Technology tech : city.partialScience)
			{
				scienceListModel.addElement("Learning "+tech.name());
			}
		}
		if (city.hasScience())
		{
			for (Technology tech : city.science)
			{
				scienceListModel.addElement("Learned "+tech.name());
			}
		}
		scienceList.setModel(scienceListModel);
	}

	private void onCloseClicked()
	{
		dispose();
	}

	private void onRenameClicked()
	{
		JTextField nameField = new JTextField();
		JComponent [] inputs = new JComponent[] {
			new JLabel("Name"),
			nameField
			};

		int rv = JOptionPane.showOptionDialog(this, inputs,
			"Rename City",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE, null, null, null);
		if (rv != JOptionPane.OK_OPTION)
			return;

		try {
		if (nameField.getText().length() == 0)
			throw new Exception("You must enter a name.");

		client.setCityName(cityLocation, nameField.getText());
		}
		catch (Exception e)
		{
			e.printStackTrace(System.err);
			JOptionPane.showMessageDialog(this, e,
				"Error",
				JOptionPane.ERROR_MESSAGE);
		}
	}

	private void onEquipClicked()
	{
		MobType type = (MobType) equipSelect.getSelectedItem();
		assert type != null;

		try {

		EquipCommand c = new EquipCommand();
		c.mobType = type;
		client.sendCityOrders(cityLocation, c);

		}
		catch (Exception e)
		{
			e.printStackTrace(System.err);
			JOptionPane.showMessageDialog(this, e,
				"Error",
				JOptionPane.ERROR_MESSAGE);
		}
	}

	private void onDevelopClicked()
	{
		ZoneType type = (ZoneType) developSelect.getSelectedItem();
		assert type != null;

		try {

		DevelopCommand c = new DevelopCommand();
		c.fromZoneType = ZoneType.NATURAL;
		c.toZoneType = type;
		client.sendCityOrders(cityLocation, c);

		}
		catch (Exception e)
		{
			e.printStackTrace(System.err);
			JOptionPane.showMessageDialog(this, e,
				"Error",
				JOptionPane.ERROR_MESSAGE);
		}
	}

	private class MyListener
		implements MapModel.Listener, Client.Listener
	{
		//implements MapModel.Listener
		public void regionUpdated(Location loc)
		{
			if (!loc.equals(cityLocation))
				return; //not interested

			SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				reloadCityInfo();
			}
			});
		}

		//implements Client.Listener
		public void cityMessage(Location loc, final String message)
		{
			if (!loc.equals(cityLocation))
				return; //not interested

			SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				addCityMessage(message);
			}
			});
		}

		//implements Client.Listener
		public void cityUpdated(Location loc, final CityInfo cityData)
		{
			if (!loc.equals(cityLocation))
				return; //not interested

			SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				loadCityInfo(cityData);
			}
			});
		}

		//implements Client.Listener
		public void mobMessage(String mobName, String message)
		{
			//not interested
		}
	}
}
