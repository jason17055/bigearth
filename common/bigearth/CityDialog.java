package bigearth;

import java.io.*;
import java.net.URL;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

public class CityDialog extends JDialog
	implements CityZonesView.Listener
{
	Client client;
	Location cityLocation;
	MyListener listner;

	// {{{ land pane

	CityZonesView zonesView;

	// }}}

	// {{{ people pane

	JLabel populationLbl;
	JLabel childrenLbl;;

	// }}}

	// {{{ stock pane

	static class StockItem
	{
		JLabel iconLbl;
		JLabel typeLbl;
		JLabel quantityLbl;
	}

	JPanel stockPane;
	int nextStockItemRow = 0;
	Map<CommodityType, StockItem> stockItemLabels = new HashMap<CommodityType, StockItem>();

	// }}}

	JComboBox<MobType> equipSelect;
	DefaultListModel<String> messagesListModel;
	JList<String> messagesList;
	JLabel scientistsLbl;
	DefaultListModel<String> scienceListModel;
	JList<String> scienceList;

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

		JButton refreshBtn = new JButton("Refresh");
		refreshBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onRefreshClicked();
			}});
		buttonPane.add(refreshBtn);

		equipSelect = new JComboBox<MobType>(equipChoices);
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

		scienceListModel = new DefaultListModel<String>();

		scienceList = new JList<String>(scienceListModel);
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

		messagesListModel = new DefaultListModel<String>();

		messagesList = new JList<String>(messagesListModel);
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

		StockItem item = new StockItem();

		URL stockIconUrl = ct.getIconResource();
		ImageIcon stockIcon = stockIconUrl != null ? new ImageIcon(stockIconUrl) : null;
		item.iconLbl = new JLabel(stockIcon);
		stockPane.add(item.iconLbl, c1);

		item.typeLbl = new JLabel(ct.getDisplayName());
		stockPane.add(item.typeLbl, c2);

		item.quantityLbl = new JLabel();
		stockPane.add(item.quantityLbl, c3);

		stockItemLabels.put(ct, item);
	}

	private void hideStockItem(CommodityType ct)
	{
		StockItem item = stockItemLabels.get(ct);
		if (item != null)
		{
			item.iconLbl.setVisible(false);
			item.typeLbl.setVisible(false);
			item.quantityLbl.setVisible(false);
		}
	}

	private void updateStockItem(CommodityType ct, long qty)
	{
		if (!stockItemLabels.containsKey(ct))
		{
			addStockItem(ct);
		}

		StockItem item = stockItemLabels.get(ct);
		item.quantityLbl.setText(Long.toString(qty));
		item.iconLbl.setVisible(true);
		item.typeLbl.setVisible(true);
		item.quantityLbl.setVisible(true);
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
		JPanel mainPane = new JPanel(new BorderLayout());

		zonesView = new CityZonesView();
		zonesView.addListener(this);
		mainPane.add(zonesView, BorderLayout.CENTER);

		JPanel buttonPane = new JPanel();
		mainPane.add(buttonPane, BorderLayout.SOUTH);

		JButton newBtn = new JButton("New...");
		newBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onNewLandClicked();
			}});
		buttonPane.add(newBtn);

		JButton examineLandBtn = new JButton("Examine");
		examineLandBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onExamineLandClicked();
			}});
		buttonPane.add(examineLandBtn);

		JButton destroyZoneBtn = new JButton("Destroy");
		destroyZoneBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onDestroyZoneClicked();
			}});
		buttonPane.add(destroyZoneBtn);

		return mainPane;
	}

	class ZoneItem
	{
		String name;
		ZoneType type;
		CommodityType commodity;
		CommodityRecipe recipe;
	}

	private boolean updateZoneItem(ZoneItem zi, ZoneInfo zone)
	{
		boolean anyChange = false;

		if (zi.type != zone.type)
		{
			zi.type = zone.type;
			anyChange = true;
		}
		if (zi.commodity != zone.commodity)
		{
			zi.commodity = zone.commodity;
			anyChange = true;
		}
		if (zi.recipe != zone.recipe)
		{
			zi.recipe = zone.recipe;
			anyChange = true;
		}

		return anyChange;
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

	private void loadCityInfo_people(CityInfo city)
	{
		populationLbl.setText(city.hasPopulation() ?
			Integer.toString(city.population) : null);
		childrenLbl.setText(city.hasChildren() ?
			Integer.toString(city.children) : null);
	}

	private void loadCityInfo_land(CityInfo city)
	{
		assert city.hasZones();

		zonesView.update(city);
	}

	private void loadCityInfo_stock(CityInfo city)
	{
		assert city.hasStock();
		for (CommodityType ct : city.stock.getCommodityTypesArray())
		{
			updateStockItem(ct, city.stock.getQuantity(ct));
		}
		for (CommodityType ct : stockItemLabels.keySet())
		{
			if (city.stock.getQuantity(ct) == 0)
			{
				hideStockItem(ct);
			}
		}
	}

	private void loadCityInfo(CityInfo city)
	{
		setTitle("City: "+city.displayName);

		loadCityInfo_land(city);
		loadCityInfo_people(city);
		loadCityInfo_stock(city);

		scientistsLbl.setText(city.hasScientists() ?
			"This city has "+city.scientists+" scientists" : null);
		scienceListModel = new DefaultListModel<String>();
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

	private void onRefreshClicked()
	{
		reloadCityInfo();
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

	private void examineLand_pasture(String zoneName, ZoneInfo zone)
	{
		CommodityType [] commodities = new CommodityType [] {
			CommodityType.SHEEP,
			CommodityType.PIG,
			CommodityType.CATTLE,
			CommodityType.HORSE
			};
		String [] choices = new String [commodities.length+1];
		int myChoice = 0;
		choices[0] = "--None--";
		for (int i = 0; i < commodities.length; i++)
		{
			if (zone.commodity == commodities[i])
				myChoice = i+1;
			choices[i+1] = commodities[i].getDisplayName();
		}

		JComboBox<String> select = new JComboBox<>(choices);
		select.setSelectedIndex(myChoice);
		JComponent [] inputs = new JComponent[] {
			new JLabel("Choose livestock type for this pasture"),
			select
			};

		int rv = JOptionPane.showOptionDialog(this, inputs,
			"Examine Zone",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE, null, null, null);
		if (rv != JOptionPane.OK_OPTION)
			return;

		try
		{

		int selectIdx = select.getSelectedIndex();

		SetZoneStorageCommand c = new SetZoneStorageCommand();
		c.zone = zoneName;
		c.commodity = selectIdx >= 1 ? commodities[selectIdx-1] : null;
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

	private void examineLand_workshop(String zoneName, ZoneInfo zone)
	{
		String [] choices = new String [] {
			"--None--",
			CommodityType.STONE_BLOCK.getDisplayName(),
			CommodityType.STONE_WEAPON.getDisplayName()
			};
		JComboBox<String> select = new JComboBox<>(choices);
		select.setSelectedIndex(
			zone.recipe == CommodityRecipe.STONE_TO_STONE_BLOCK ? 1 :
			zone.recipe == CommodityRecipe.STONE_TO_STONE_WEAPON ? 2 : 0);
		JComponent [] inputs = new JComponent[] {
			new JLabel("Choose product for this stone workshop"),
			select
			};

		int rv = JOptionPane.showOptionDialog(this, inputs,
			"Examine Zone",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE, null, null, null);
		if (rv != JOptionPane.OK_OPTION)
			return;

		try
		{

		int selectIdx = select.getSelectedIndex();

		SetFactoryRecipeCommand c = new SetFactoryRecipeCommand();
		c.zone = zoneName;
		c.recipe = selectIdx == 1 ? CommodityRecipe.STONE_TO_STONE_BLOCK :
			selectIdx == 2 ? CommodityRecipe.STONE_TO_STONE_WEAPON :
			null;
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

	private void onDestroyZoneClicked()
	{
		String zoneName = zonesView.selectedZone;
		if (zoneName == null)
			return;

		try
		{
		DestroyZoneCommand c = new DestroyZoneCommand();
		c.zone = zoneName;
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

	private void onExamineLandClicked()
	{
		String zoneName = zonesView.selectedZone;
		if (zoneName == null)
			return;

		ZoneInfo zone = zonesView.zones.get(zoneName);
		if (zone == null)
			return;

		if (zone.type == ZoneType.STONE_WORKSHOP)
		{
			examineLand_workshop(zoneName, zone);
			return;
		}
		else if (zone.type == ZoneType.PASTURE)
		{
			examineLand_pasture(zoneName, zone);
			return;
		}

		JOptionPane.showMessageDialog(this,
			"You selected " + zoneName,
			"Examine Zone",
			JOptionPane.PLAIN_MESSAGE);
	}

	private void onNewLandClicked()
	{
		ZoneType type = NewZoneDialog.showNewZoneDialog(this, client, cityLocation);
		if (type == null)
			return;

		zonesView.showNewBuildingCursor(type);
	}

	//implements CityZonesView.Listener
	public void newBuildingRequested(ZoneType type, int gridx, int gridy)
	{
		try {

		DevelopCommand c = new DevelopCommand();
		c.gridx = gridx;
		c.gridy = gridy;
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
