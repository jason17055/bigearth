package bigearth;

import java.awt.*;
import javax.swing.*;
import java.awt.event.*;

public class MainWindow extends JFrame
	implements Client.Listener, MobListModel.Listener
{
	MapModel map;
	MobListModel mobList;
	WorldView view;
	Client client;
	JPanel mobPane;
	JLabel mobTypeLbl;
	JLabel mobEncumbranceLbl;
	JLabel mobHungerLbl;
	JLabel mobStockWoodLbl;
	JLabel mobStockMeatLbl;
	JLabel mobStockSheepLbl;
	JLabel mobStockPigLbl;

	static final int SIDE_BAR_WIDTH = 180;

	public MainWindow(Client client)
	{
		super("Big Earth");

		this.client = client;

		view = new WorldView() {
			public void onRightMouseClick(int regionId)
			{
				moveMobTo(regionId);
			}
			public void onSelectionChanged()
			{
				super.onSelectionChanged();
				if (selection.isMob())
				{
					MainWindow.this.onMobSelected();
				}
				else if (selection.isCity())
				{
					MainWindow.this.onCitySelected();
				}
				else
				{
					MainWindow.this.onOtherSelected();
				}
			}

			};
		view.zoomFactor = 8;
		add(view, BorderLayout.CENTER);

		JPanel sideBar = new JPanel();
		sideBar.setLayout(new BoxLayout(sideBar, BoxLayout.PAGE_AXIS));
		setSideBarDimensions(sideBar);
		add(sideBar, BorderLayout.WEST);

		mobPane = new JPanel();
		mobPane.setVisible(false);
		initMobPane();
		sideBar.add(mobPane);

		initMenu();

		pack();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setLocationRelativeTo(null);

		addWindowListener(new WindowAdapter() {
			public void windowClosed(WindowEvent ev)
			{
				onWindowClosed();
			}
			});

		client.addListener(this);
	}

	private void initMobPane()
	{
		mobPane.setLayout(new GridBagLayout());
		GridBagConstraints c1 = new GridBagConstraints();
		c1.gridx = 0;
		c1.anchor = GridBagConstraints.FIRST_LINE_START;
		c1.weightx = 1.0;

		GridBagConstraints c2 = new GridBagConstraints();
		c2.gridx = 1;
		c2.anchor = GridBagConstraints.FIRST_LINE_END;

		c1.gridy = c2.gridy = 0;
		mobPane.add(new JLabel("Type"), c1);

		mobTypeLbl = new JLabel();
		mobPane.add(mobTypeLbl, c2);

		c1.gridy = c2.gridy = 1;
		mobPane.add(new JLabel("Encumbrance"), c1);

		mobEncumbranceLbl = new JLabel();
		mobPane.add(mobEncumbranceLbl, c2);

		c1.gridy = c2.gridy = 2;
		mobPane.add(new JLabel("Hunger"), c1);

		mobHungerLbl = new JLabel();
		mobPane.add(mobHungerLbl, c2);

		c1.gridy = ++c2.gridy;
		mobPane.add(new JLabel("Wood"), c1);

		mobStockWoodLbl = new JLabel();
		mobPane.add(mobStockWoodLbl, c2);

		c1.gridy = ++c2.gridy;
		mobPane.add(new JLabel("Meat"), c1);

		mobStockMeatLbl = new JLabel();
		mobPane.add(mobStockMeatLbl, c2);

		c1.gridy = ++c2.gridy;
		mobPane.add(new JLabel("Sheep"), c1);

		mobStockSheepLbl = new JLabel();
		mobPane.add(mobStockSheepLbl, c2);

		c1.gridy = ++c2.gridy;
		mobPane.add(new JLabel("Pig"), c1);

		mobStockPigLbl = new JLabel();
		mobPane.add(mobStockPigLbl, c2);
	}

	private void setSideBarDimensions(JPanel sideBar)
	{
		Dimension d = new Dimension(sideBar.getMinimumSize());
		d.width = SIDE_BAR_WIDTH;
		sideBar.setMinimumSize(d);

		d = new Dimension(sideBar.getPreferredSize());
		d.width = SIDE_BAR_WIDTH;
		sideBar.setPreferredSize(d);

		d = new Dimension(sideBar.getMaximumSize());
		d.width = SIDE_BAR_WIDTH;
		sideBar.setMaximumSize(d);
	}

	void onWindowClosed()
	{
		client.removeListener(this);
	}

	void initMenu()
	{
		JMenuBar menuBar = new JMenuBar();

		JMenu gameMenu = new JMenu("Game");
		menuBar.add(gameMenu);

		JMenuItem menuItem;
		menuItem = new JMenuItem("Exit");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onExitClicked();
			}});
		gameMenu.add(menuItem);

		JMenu viewMenu = new JMenu("View");
		menuBar.add(viewMenu);

		menuItem = new JMenuItem("Zoom In");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onZoomInClicked();
			}});
		viewMenu.add(menuItem);

		menuItem = new JMenuItem("Zoom Out");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onZoomOutClicked();
			}});
		viewMenu.add(menuItem);

		JMenu ordersMenu = new JMenu("Orders");
		menuBar.add(ordersMenu);

		menuItem = new JMenuItem("Hunt");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onSimpleOrderClicked("hunt");
			}});
		ordersMenu.add(menuItem);

		menuItem = new JMenuItem("Gather Wood");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onSimpleOrderClicked("gather-wood");
			}});
		ordersMenu.add(menuItem);

		menuItem = new JMenuItem("Build City");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onSimpleOrderClicked("build-city");
			}});
		ordersMenu.add(menuItem);

		menuItem = new JMenuItem("Disband");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onSimpleOrderClicked("disband");
			}});
		ordersMenu.add(menuItem);

		menuItem = new JMenuItem("Inventory...");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onInventoryClicked();
			}});
		ordersMenu.add(menuItem);

		menuItem = new JMenuItem("Flag...");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onFlagClicked();
			}});
		ordersMenu.add(menuItem);

		setJMenuBar(menuBar);
	}

	private void onExitClicked()
	{
		dispose();
	}

	private void onZoomInClicked()
	{
		view.zoomIn();
	}

	private void onZoomOutClicked()
	{
		view.zoomOut();
	}

	public void setMap(MapModel map)
	{
		this.map = map;
		this.view.setMap(map);
	}

	public void setMobList(MobListModel mobList)
	{
		this.mobList = mobList;
		this.mobList.addListener(this);
		this.view.setMobs(mobList);

		// find a unit to focus on
		MobInfo [] mobs = mobList.mobs.values().toArray(new MobInfo[0]);
		if (mobs.length != 0)
		{
			MobInfo mob = mobs[0];
			view.panTo(mob.location);
			return;
		}

		// no units, look for a city
		assert map != null;
		for (Location loc : map.regions.keySet())
		{
			RegionProfile region = map.getRegion(loc);
			if (region.hasCitySize())
			{
				view.panTo(loc);
				return;
			}
		}
	}

	void onOtherSelected()
	{
		mobPane.setVisible(false);
	}

	private void loadMobInfo(String mobName)
	{
		MobInfo mob = mobList.mobs.get(mobName);
		mobTypeLbl.setText(mob.hasMobType() ?
			mob.mobType.name().toLowerCase() : "");

		mobEncumbranceLbl.setText(mob.hasEncumbrance() ?
			mob.encumbrance.name().toLowerCase() : "");
		mobHungerLbl.setText(mob.hasHunger() ?
			mob.hunger.name().toLowerCase() : "");

		mobStockWoodLbl.setText(mob.hasStock() ?
			Long.toString(mob.getStock(CommodityType.WOOD)) : "");
		mobStockMeatLbl.setText(mob.hasStock() ?
			Long.toString(mob.getStock(CommodityType.MEAT)) : "");
		mobStockSheepLbl.setText(mob.hasStock() ?
			Long.toString(mob.getStock(CommodityType.SHEEP)) : "");
		mobStockPigLbl.setText(mob.hasStock() ?
			Long.toString(mob.getStock(CommodityType.PIG)) : "");
	}

	void onCitySelected()
	{
		Location cityLocation = view.selection.getCity();
		CityDialog dlg = new CityDialog(this, client, cityLocation);
		dlg.setVisible(true);
	}

	void onMobSelected()
	{
		String mobName = view.selection.getMob();
		mobPane.setBorder(
			BorderFactory.createTitledBorder(mobName)
			);

		loadMobInfo(mobName);

		mobPane.setVisible(true);
	}

	void moveMobTo(int regionId)
	{
		if (!view.selection.isMob())
		{
			// the user has not selected a mob
			System.out.println("not a mob");
			return;
		}

		Location loc = new SimpleLocation(regionId);

		System.out.println("want to move "+view.selection.getMob()+" to "+loc);
		try
	{
		client.moveMobTo(view.selection.getMob(), loc);
		}
		catch (Exception e)
		{
			System.out.println(e);
		}
	}

	void onSimpleOrderClicked(String orderName)
	{
		if (!view.selection.isMob())
			return;

		try
		{
			client.setMobActivity(view.selection.getMob(), orderName);
		}
		catch (Exception e)
		{
			e.printStackTrace(System.err);

			JOptionPane.showMessageDialog(this, e,
				"Error",
				JOptionPane.ERROR_MESSAGE);
		}
	}

	//implements Client.Listener
	public void cityUpdated(Location cityLocation, CityInfo cityData)
	{
		//TODO
	}

	//implements Client.Listener
	public void cityMessage(Location cityLocation, String message)
	{
		//TODO
	}

	//implements Client.Listener
	public void mobMessage(String mobName, String message)
	{
		JOptionPane.showMessageDialog(this, message,
			mobName, JOptionPane.INFORMATION_MESSAGE);
	}

	void onFlagClicked()
	{
		if (!view.selection.isMob())
			return;

		Flag [] flagList = new Flag[] {
			Flag.NONE, Flag.RED, Flag.BLUE
			};
		JComboBox flagSelect = new JComboBox(flagList);
		flagSelect.setSelectedIndex(0);

		JComponent [] inputs = new JComponent[] {
			new JLabel("Flag"),
			flagSelect
			};

		int rv = JOptionPane.showOptionDialog(this, inputs,
			"Flag",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE, null, null, null);
		if (rv != JOptionPane.OK_OPTION)
			return;

		try
		{
			Flag flag = flagList[flagSelect.getSelectedIndex()];
			client.setMobFlag(view.selection.getMob(), flag);
		}
		catch (Exception e)
		{
			e.printStackTrace(System.err);

			JOptionPane.showMessageDialog(this, e,
				"Error",
				JOptionPane.ERROR_MESSAGE);
		}
	}

	void onInventoryClicked()
	{
		if (!view.selection.isMob())
			return;

		InventoryDialog dlg = new InventoryDialog(this, client, view.selection.getMob());
		dlg.setVisible(true);
	}

	// implements MobListModel.Listener
	public void mobRemoved(String mobName, MobInfo.RemovalDisposition disposition)
	{
		//nothing needed
	}

	// implements MobListModel.Listener
	public void mobUpdated(String mobName)
	{
		if (view.selection.isMob()
		&& view.selection.getMob().equals(mobName))
		{
			loadMobInfo(mobName);
		}
	}

}
