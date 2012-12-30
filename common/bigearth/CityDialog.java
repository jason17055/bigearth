package bigearth;

import java.io.*;
import java.net.URL;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

public class CityDialog extends JDialog
{
	Client client;
	Location cityLocation;
	MyListener listner;

	// {{{ land pane

	JList<ZoneItem> landList;
	DefaultListModel<ZoneItem> landListModel;

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

	static ZoneType [] developChoices = new ZoneType[] {
		ZoneType.MUD_COTTAGE,
		ZoneType.WOOD_COTTAGE,
		ZoneType.STONE_COTTAGE,
		ZoneType.FARM,
		ZoneType.PASTURE,
		ZoneType.STONE_WORKSHOP
		};
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

		landListModel = new DefaultListModel<ZoneItem>();
		landList = new JList<ZoneItem>(landListModel);
		landList.setCellRenderer(new MyZoneCellRenderer());
		landList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		landList.setVisibleRowCount(-1);
		landList.setLayoutOrientation(JList.HORIZONTAL_WRAP);

		JScrollPane landListScroll = new JScrollPane(landList);
		landListScroll.setPreferredSize(new Dimension(550,212));
		mainPane.add(landListScroll, BorderLayout.CENTER);

		JPanel buttonPane = new JPanel();
		mainPane.add(buttonPane, BorderLayout.SOUTH);

		JButton examineLandBtn = new JButton("Examine");
		examineLandBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onExamineLandClicked();
			}});
		buttonPane.add(examineLandBtn);

		JButton newBtn = new JButton("New...");
		newBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onNewLandClicked();
			}});
		buttonPane.add(newBtn);

		return mainPane;
	}

	class ZoneItem
	{
		String name;
		ZoneType type;
		CommodityRecipe recipe;
	}

	static class MyZoneCellRenderer implements ListCellRenderer<ZoneItem>
	{
		JPanel mainPane;
		JLabel iconLabel;
		JLabel typeLabel;
		JLabel detailLabel;

		public MyZoneCellRenderer()
		{
			mainPane = new JPanel();
			mainPane.setLayout(new BorderLayout());
			mainPane.setOpaque(true);

			JPanel gridPane = new JPanel();
			gridPane.setLayout(new GridBagLayout());
			gridPane.setBorder(
				BorderFactory.createEmptyBorder(4,4,4,4)
				);
			gridPane.setOpaque(false);

			GridBagConstraints c = new GridBagConstraints();

			iconLabel = new JLabel();
			c.gridx = c.gridy = 0;
			c.gridwidth = 1;
			c.gridheight = 2;
			c.fill = GridBagConstraints.BOTH;
			c.insets = new Insets(0, 0, 0, 6);
			gridPane.add(iconLabel, c);

			typeLabel = new JLabel();
			c.gridx = 1;
			c.gridheight = 1;
			c.anchor = GridBagConstraints.SOUTHWEST;
			c.weightx = 1.0;
			c.weighty = 0.5;
			c.fill = GridBagConstraints.NONE;
			c.insets = new Insets(0, 0, 0, 0);
			gridPane.add(typeLabel, c);

			detailLabel = new JLabel();
			Font f = detailLabel.getFont();
			detailLabel.setFont(f.deriveFont(
					f.getStyle() & ~Font.BOLD,
					(float)(f.getSize2D() * 0.85)));
			c.gridx = 1;
			c.gridy = 1;
			c.fill = GridBagConstraints.NONE;
			c.anchor = GridBagConstraints.NORTHWEST;
			gridPane.add(detailLabel, c);

			mainPane.add(gridPane);
		}

		//implements ListCellRenderer
		public Component getListCellRendererComponent(JList<? extends ZoneItem> list, ZoneItem zi, int index, boolean isSelected, boolean cellHasFocus)
		{
			URL zoneIconUrl = zi.type.getIconResource();
			ImageIcon zoneIcon = zoneIconUrl != null ? new ImageIcon(zoneIconUrl) : null;
			iconLabel.setIcon(zoneIcon);
			typeLabel.setText(zi.type.getDisplayName());

			if (zi.recipe != null)
			{
				// \u2192 is Unicode rightwards arrow
				detailLabel.setText("\u2192"+zi.recipe.getOutputCommodity().getDisplayName());
			}
			else
			{
				detailLabel.setText(null);
			}

			if (isSelected)
			{
				mainPane.setBackground(list.getSelectionBackground());
				mainPane.setForeground(list.getSelectionForeground());
			}
			else
			{
				mainPane.setBackground(list.getBackground());
				mainPane.setForeground(list.getForeground());
			}

			Border b = null;
			if (cellHasFocus)
			{
				if (isSelected)
					b = UIManager.getBorder("List.focusSelectedCellHighlightBorder");
				if (b == null)
					b = UIManager.getBorder("List.focusCellHighlightBorder");
			}
			else
			{
				b = NO_FOCUS_BORDER;
			}
			mainPane.setBorder(b);

			return mainPane;
		}

		static final Border NO_FOCUS_BORDER = new EmptyBorder(1,1,1,1);
	}

	private boolean updateZoneItem(ZoneItem zi, ZoneInfo zone)
	{
		boolean anyChange = false;

		if (zi.type != zone.type)
		{
			zi.type = zone.type;
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

		//reverse order so that removing items will not mess up our indexes
		Set<String> seen = new HashSet<>();
		for (int i = landListModel.size()-1; i >= 0; i--)
		{
			ZoneItem zi = landListModel.get(i);
			if (city.zones.containsKey(zi.name))
			{
				// check whether it needs to be updated
				if (updateZoneItem(zi, city.zones.get(zi.name)))
				{
					landListModel.set(i, zi);
				}
				seen.add(zi.name);
			}
			else
			{
				landListModel.removeElementAt(i);
			}
		}

		for (Map.Entry<String,ZoneInfo> e : city.zones.entrySet())
		{
			String zoneName = e.getKey();
			ZoneInfo zone = e.getValue();

			if (zone.type == ZoneType.NATURAL)
				continue;

			if (!seen.contains(zoneName))
			{
				ZoneItem zi = new ZoneItem();
				zi.name = zoneName;
				updateZoneItem(zi, zone);
				landListModel.addElement(zi);
			}
		}
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

	private void examineLand_workshop(ZoneItem zi)
	{
		String [] choices = new String [] {
			"--None--",
			CommodityType.STONE_BLOCK.getDisplayName(),
			CommodityType.STONE_WEAPON.getDisplayName()
			};
		JComboBox<String> select = new JComboBox<>(choices);
		select.setSelectedIndex(
			zi.recipe == CommodityRecipe.STONE_TO_STONE_BLOCK ? 1 :
			zi.recipe == CommodityRecipe.STONE_TO_STONE_WEAPON ? 2 : 0);
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
		c.zone = zi.name;
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

	private void onExamineLandClicked()
	{
		ZoneItem zi = landList.getSelectedValue();
		if (zi == null)
			return;

		if (zi.type == ZoneType.STONE_WORKSHOP)
		{
			examineLand_workshop(zi);
			return;
		}

		JOptionPane.showMessageDialog(this,
			"You selected " + zi.name,
			"Examine Zone",
			JOptionPane.PLAIN_MESSAGE);
	}

	private void onNewLandClicked()
	{
		JComboBox<ZoneType> developSelect = new JComboBox<>(developChoices);
		JComponent [] inputs = new JComponent[] {
			new JLabel("Type of land to develop"),
			developSelect
			};

		int rv = JOptionPane.showOptionDialog(this, inputs,
			"Develop Land",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE, null, null, null);
		if (rv != JOptionPane.OK_OPTION)
			return;

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
