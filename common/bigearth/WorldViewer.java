package bigearth;

import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.vecmath.*;

public class WorldViewer extends JFrame
	implements ActionListener, WorldView.Listener
{
	public static void main(String [] args)
		throws Exception
	{
		WorldViewer wv = new WorldViewer();

		File f = new File("world1");
		if (args.length >= 1)
		{
			f = new File(args[0]);
		}

		if (f.exists())
		{
			wv.setWorld(MakeWorld.load(f));
		}

		wv.setVisible(true);
	}

	WorldView view;
	MakeRivers mrivers;
	JButton expandLakeBtn;
	JButton stepBtn;
	JCheckBoxMenuItem showElevationBtn;
	JCheckBoxMenuItem showTemperatureBtn;
	JCheckBoxMenuItem showRainfallBtn;
	JCheckBoxMenuItem showFloodsBtn;
	JCheckBoxMenuItem showRiversBtn;
	JCheckBoxMenuItem showWildlifeBtn;
	JButton zoomInBtn;
	JButton zoomOutBtn;

	Map<MapProjection, JRadioButtonMenuItem> projectionMenuItems = 
			new EnumMap<MapProjection,JRadioButtonMenuItem>(
				MapProjection.class);

	JPanel toolsPane;
	Map<String, JToggleButton> toolBtns;

	JPanel regionPane;
	JLabel biomeLbl;
	JLabel wildlifeLbl;
	JLabel nativesLbl;
	JLabel elevationLbl;
	JLabel temperatureLbl;
	JLabel moistureLbl;
	JLabel depthLbl;

	JPanel mobPane;
	JLabel mobTypeLbl;
	JLabel mobOwnerLbl;

	String selectedMob;

	private void addToolButton(String command)
	{
		JToggleButton btn = new JToggleButton(command);
		btn.setActionCommand(command);
		btn.addActionListener(this);
		toolsPane.add(btn);
		toolBtns.put(command, btn);
	}

	WorldViewer() throws IOException
	{
		super("World Viewer");
		view = new WorldView();
		view.addListener(this);
		view.allowEdgeSelection = true;
		view.allowVertexSelection = true;
		add(view, BorderLayout.CENTER);

		toolsPane = new JPanel();
		toolsPane.setLayout(new BoxLayout(toolsPane, BoxLayout.Y_AXIS));
		add(toolsPane, BorderLayout.WEST);

		toolBtns = new HashMap<String, JToggleButton>();
		addToolButton("hand");
		addToolButton("grass");
		addToolButton("ocean");
		addToolButton("lake");

		selectedTool = "hand";
		toolBtns.get("hand").setSelected(true);

		regionPane = new JPanel();
		regionPane.setVisible(false);
		initRegionPane();
		toolsPane.add(regionPane);

		mobPane = new JPanel();
		mobPane.setVisible(false);
		initMobPane();
		toolsPane.add(mobPane);

		JPanel buttonsPane = new JPanel();
		add(buttonsPane, BorderLayout.SOUTH);

		expandLakeBtn = new JButton("Expand Lake");
		expandLakeBtn.addActionListener(this);
		buttonsPane.add(expandLakeBtn);

		stepBtn = new JButton("Step");
		stepBtn.addActionListener(this);
		buttonsPane.add(stepBtn);

		zoomInBtn = new JButton("Zoom In");
		zoomInBtn.addActionListener(this);
		buttonsPane.add(zoomInBtn);

		zoomOutBtn = new JButton("Zoom Out");
		zoomOutBtn.addActionListener(this);
		buttonsPane.add(zoomOutBtn);

		initMenu();
		setMapProjection(MapProjection.SIMPLE);

		pack();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setLocationRelativeTo(null);
		addWindowListener(new WindowAdapter() {
			public void windowClosed(WindowEvent ev) {
				onWindowClosed();
			}});
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
		mobPane.add(new JLabel("Owner"), c1);

		mobOwnerLbl = new JLabel();
		mobPane.add(mobOwnerLbl, c2);
	}

	private void initRegionPane()
	{
		regionPane.setLayout(new GridBagLayout());
		GridBagConstraints c_l = new GridBagConstraints();
		c_l.gridx = 0;
		c_l.anchor = GridBagConstraints.FIRST_LINE_START;

		GridBagConstraints c_r = new GridBagConstraints();
		c_r.gridx = 1;
		c_r.anchor = GridBagConstraints.FIRST_LINE_END;

		c_l.gridy = c_r.gridy = 0;
		regionPane.add(new JLabel("Biome"), c_l);

		biomeLbl = new JLabel();
		regionPane.add(biomeLbl, c_r);

		c_l.gridy = c_r.gridy = 1;
		regionPane.add(new JLabel("Wildlife"), c_l);

		wildlifeLbl = new JLabel();
		regionPane.add(wildlifeLbl, c_r);

		c_l.gridy = c_r.gridy = 2;
		regionPane.add(new JLabel("Natives"), c_l);

		nativesLbl = new JLabel();
		regionPane.add(nativesLbl, c_r);

		c_l.gridy = c_r.gridy = 3;
		regionPane.add(new JLabel("Elevation"), c_l);

		elevationLbl = new JLabel();
		regionPane.add(elevationLbl, c_r);

		c_l.gridy = c_r.gridy = 4;
		regionPane.add(new JLabel("Temperature"), c_l);

		temperatureLbl = new JLabel();
		regionPane.add(temperatureLbl, c_r);

		c_l.gridy = c_r.gridy = 5;
		regionPane.add(new JLabel("Moisture"), c_l);

		moistureLbl = new JLabel();
		regionPane.add(moistureLbl, c_r);

		c_l.gridy = c_r.gridy = 6;
		regionPane.add(new JLabel("Water Depth"), c_l);

		depthLbl = new JLabel();
		regionPane.add(depthLbl, c_r);
	}

	void setMapProjection(MapProjection newProjection)
	{
		{
			JRadioButtonMenuItem btn = projectionMenuItems.get(view.curProjection);
			if (btn != null) {
				btn.setSelected(false);
			}
		}
		view.setProjection(newProjection);
		{
			JRadioButtonMenuItem btn = projectionMenuItems.get(view.curProjection);
			if (btn != null) {
				btn.setSelected(true);
			}
		}
	}

	void setWorld(MakeWorld newWorld)
	{
		world = newWorld;
		view.setMap(new MapAdapter(this.world));
		view.setMobs(new MobListAdapter(this.world));
		reloadImage();
	}

	private void onWindowClosed()
	{
		try
		{
			world.save();
		}
		catch (Exception e)
		{
			e.printStackTrace(System.err);
		}
	}

	private void onNewWorldClicked()
	{
		try
		{

		JTextField nameField = new JTextField();
		JTextField sizeField = new JTextField();
		final JComponent[] inputs = new JComponent[] {
			new JLabel("Name"),
			nameField,
			new JLabel("Size"),
			sizeField
			};

		int rv = JOptionPane.showOptionDialog(this, inputs,
			"New World",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE, null, null, null);
		if (rv != JOptionPane.OK_OPTION)
			return;

		if (nameField.getText().length() == 0)
			throw new Exception("You must enter a name.");

		int sz = Integer.parseInt(sizeField.getText());

		File worldDir = new File(nameField.getText());

		setWorld(
			MakeWorld.create(worldDir, sz)
			);

		}
		catch (Exception e)
		{
			e.printStackTrace(System.err);

			JOptionPane.showMessageDialog(this, e,
				"Error",
				JOptionPane.ERROR_MESSAGE);
		}
	}

	protected void initMenu()
	{
		JMenuBar menuBar = new JMenuBar();

		JMenu fileMenu = new JMenu("World");
		menuBar.add(fileMenu);

		JMenuItem menuItem;
		menuItem = new JMenuItem("New World...");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onNewWorldClicked();
			}});
		fileMenu.add(menuItem);

		menuItem = new JMenuItem("Generate Terrain");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onGenerateTerrainClicked();
			}});
		fileMenu.add(menuItem);

		menuItem = new JMenuItem("Generate Rivers");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onGenerateRiversClicked();
			}});
		fileMenu.add(menuItem);

		menuItem = new JMenuItem("Generate Floods");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onGenerateFloodsClicked();
			}});
		fileMenu.add(menuItem);

		menuItem = new JMenuItem("Generate Biomes");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onGenerateBiomesClicked();
			}});
		fileMenu.add(menuItem);

		menuItem = new JMenuItem("Generate Minerals");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onGenerateMineralsClicked();
			}});
		fileMenu.add(menuItem);

		menuItem = new JMenuItem("Leaders...");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onLeadersClicked();
			}});
		fileMenu.add(menuItem);

		JMenu viewMenu = new JMenu("View");
		menuBar.add(viewMenu);

		ActionListener al = new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onViewOptionsChanged(ev);
			}};

		showElevationBtn = new JCheckBoxMenuItem("Show Elevations");
		showElevationBtn.addActionListener(al);
		viewMenu.add(showElevationBtn);

		showTemperatureBtn = new JCheckBoxMenuItem("Show Temperatures");
		showTemperatureBtn.addActionListener(al);
		viewMenu.add(showTemperatureBtn);

		showRainfallBtn = new JCheckBoxMenuItem("Show Rains");
		showRainfallBtn.addActionListener(al);
		viewMenu.add(showRainfallBtn);

		showFloodsBtn = new JCheckBoxMenuItem("Show Floods");
		showFloodsBtn.addActionListener(al);
		viewMenu.add(showFloodsBtn);

		showRiversBtn = new JCheckBoxMenuItem("Show Rivers");
		showRiversBtn.addActionListener(al);
		showRiversBtn.setSelected(true);
		viewMenu.add(showRiversBtn);

		showWildlifeBtn = new JCheckBoxMenuItem("Show Wildlife");
		showWildlifeBtn.addActionListener(al);
		viewMenu.add(showWildlifeBtn);

		JMenu projectionMenu = new JMenu("Projection");
		viewMenu.add(projectionMenu);

		JRadioButtonMenuItem radio;
		radio = new JRadioButtonMenuItem("Simple");
		projectionMenuItems.put(MapProjection.SIMPLE, radio);
		radio.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				setMapProjection(MapProjection.SIMPLE);
			}});
		projectionMenu.add(radio);

		radio = new JRadioButtonMenuItem("Orthographic");
		projectionMenuItems.put(MapProjection.ORTHOGRAPHIC, radio);
		radio.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				setMapProjection(MapProjection.ORTHOGRAPHIC);
			}});
		projectionMenu.add(radio);

		JMenu regionMenu = new JMenu("Region");
		menuBar.add(regionMenu);

		menuItem = new JMenuItem("Add Wildlife");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onAdjustWildlifeClicked(+1);
			}});
		regionMenu.add(menuItem);

		menuItem = new JMenuItem("Remove Wildlife");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onAdjustWildlifeClicked(-1);
			}});
		regionMenu.add(menuItem);

		menuItem = new JMenuItem("Spawn Character...");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onSpawnCharacterClicked();
			}});
		regionMenu.add(menuItem);

		JMenu mobMenu = new JMenu("Mob");
		menuBar.add(mobMenu);

		menuItem = new JMenuItem("Set Owner...");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onSetOwnerClicked();
			}});
		mobMenu.add(menuItem);

		menuItem = new JMenuItem("Set Type...");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onSetMobTypeClicked();
			}});
		mobMenu.add(menuItem);

		setJMenuBar(menuBar);
	}

	void onAdjustWildlifeClicked(int delta)
	{
		if (view.selection.selectedRegion != 0)
		{
			RegionServant r = world.world.regions[view.selection.selectedRegion-1];
			r.adjustWildlife(delta*100);
		}
	}

	void onLeadersClicked()
	{
		new LeadersFrame(world.world, this).setVisible(true);
	}

	void onExpandLakeClicked()
	{
	try {
		if (mrivers == null)
			throw new Exception("Must generate rivers first");

		MakeRivers.LakeInfo lake = null;
		if (view.selection.selectedVertex != null)
		{
			lake = mrivers.getLakeAt(view.selection.selectedVertex);
		}
		else if (view.selection.selectedRegion != 0)
		{
			lake = mrivers.lakesByRegion.get(view.selection.selectedRegion);
		}
		
		if (lake == null)
			throw new Exception("No lake at selected location");

		if (lake.remaining <= 0)
			lake.remaining = 1;

		mrivers.processLakeExcess(lake);
		mrivers.placeLakes();
		mrivers.placeRivers();
		reloadImage();

	}
	catch (Exception e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(),
				"Error",
				JOptionPane.ERROR_MESSAGE);
		}
	}

	void onGenerateMineralsClicked()
	{
		world.generateMinerals();
		reloadImage();
	}

	void onGenerateBiomesClicked()
	{
		world.generateBiomes();
		reloadImage();
	}

	void onGenerateRiversClicked()
	{
		mrivers = new MakeRivers(world);
		mrivers.generateRivers();
		reloadImage();
	}

	void onGenerateFloodsClicked()
	{
		if (mrivers != null)
		{
			mrivers.generateFloods();
			reloadImage();
		}
	}

	void onSpawnCharacterClicked()
	{
		try
		{

		Location loc = view.getSelectedLocation();
		if (loc == null)
		{
			throw new Exception("Please select a location first.");
		}

		MobType [] mobTypeList = MobType.values();

		JTextField ownerField = new JTextField();
		JComboBox<MobType> mobTypeSelect = new JComboBox<MobType>(mobTypeList);
		mobTypeSelect.setSelectedIndex(0);

		JComponent[] inputs = new JComponent[] {
			new JLabel("Owner"),
			ownerField,
			new JLabel("Type"),
			mobTypeSelect
			};

		int rv = JOptionPane.showOptionDialog(this, inputs,
			"New Character",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE, null, null, null);
		if (rv != JOptionPane.OK_OPTION)
			return;

		if (ownerField.getText().length() == 0)
			throw new Exception("You must enter an owner.");

		RegionServant region = world.world.getRegionForLocation(loc);
		region.spawnCharacter(loc,
			mobTypeList[mobTypeSelect.getSelectedIndex()],
			ownerField.getText()
			);

		}
		catch (Exception e)
		{
			e.printStackTrace(System.err);
			JOptionPane.showMessageDialog(this, e,
				"Error",
				JOptionPane.ERROR_MESSAGE);
		}
	}

	MobInfo getSelectedMob()
	{
		if (!view.selection.isMob())
			return null;

		String mobName = view.selection.getMob();
		MobServant mob = world.world.getMob(mobName);

		return mob.makeProfileForOwner();
	}

	void onSetMobTypeClicked()
	{
		try {

		if (!view.selection.isMob())
			throw new Exception("Please select a mob first.");

		MobServant mob = world.world.getMob(view.selection.getMob());
		if (mob == null)
			throw new Exception("Please select a mob first.");

		MobType [] mobTypeList = MobType.values();
		int toSelect = 0;
		for (int i = 0; i < mobTypeList.length; i++)
		{
			if (mobTypeList[i].equals(mob.mobType))
				toSelect=i;
		}

		JComboBox<MobType> mobTypeSelect = new JComboBox<MobType>(mobTypeList);
		mobTypeSelect.setSelectedIndex(toSelect);

		JComponent [] inputs = new JComponent[] {
			new JLabel("Type"),
			mobTypeSelect
			};

		int rv = JOptionPane.showOptionDialog(this, inputs,
			"Set Type",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE, null, null, null);
		if (rv != JOptionPane.OK_OPTION)
			return;

		int sel = mobTypeSelect.getSelectedIndex();
		mob.mobType = mobTypeList[sel];

		}
		catch (Exception e)
		{
			e.printStackTrace(System.err);
			JOptionPane.showMessageDialog(this, e,
				"Error",
				JOptionPane.ERROR_MESSAGE);
		}
	}

	void onSetOwnerClicked()
	{
		try {

		if (!view.selection.isMob())
			throw new Exception("Please select a mob first.");

		MobServant mob = world.world.getMob(view.selection.getMob());

		String [] leaderNames = world.world.leaders.keySet().toArray(new String[0]);
		String [] leaderDisplayNames = new String[leaderNames.length+1];
		leaderDisplayNames[0] = "--None--";
		int toSelect = 0;
		for (int i = 0; i < leaderNames.length; i++)
		{
			leaderDisplayNames[i+1] = world.world.leaders.get(leaderNames[i]).displayName;
			if (leaderNames[i].equals(mob.owner))
				toSelect = i+1;
		}

		JComboBox<String> ownerSelect = new JComboBox<String>(leaderDisplayNames);
		ownerSelect.setSelectedIndex(toSelect);
		
		JComponent [] inputs = new JComponent[] {
			new JLabel("Owner"),
			ownerSelect
			};

		int rv = JOptionPane.showOptionDialog(this, inputs,
			"Set Owner",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.PLAIN_MESSAGE, null, null, null);
		if (rv != JOptionPane.OK_OPTION)
			return;

		int sel = ownerSelect.getSelectedIndex();
		if (sel == 0)
		{
			mob.owner = null;
		}
		else
		{
			mob.owner = leaderNames[sel-1];
		}

		}
		catch (Exception e)
		{
			e.printStackTrace(System.err);
			JOptionPane.showMessageDialog(this, e,
				"Error",
				JOptionPane.ERROR_MESSAGE);
		}
	}

	MakeWorld world;
	String selectedTool;

	void onToolSelected(String toolName)
	{
		if (toolBtns.get(toolName).isSelected())
		{
			// unselect all other tools
			for (String s : toolBtns.keySet())
			{
				if (!s.equals(toolName))
					toolBtns.get(s).setSelected(false);
			}
			selectedTool = toolName;
		}
		else
		{
			// select the hand tool
			selectedTool = "hand";
			toolBtns.get(selectedTool).setSelected(true);
		}
	}

	protected void onViewOptionsChanged(ActionEvent ev)
	{
		if (ev.getSource() == showElevationBtn)
		{
			showTemperatureBtn.setSelected(false);
			showRainfallBtn.setSelected(false);
			showFloodsBtn.setSelected(false);
			showWildlifeBtn.setSelected(false);
			reloadImage();
		}
		else if (ev.getSource() == showTemperatureBtn)
		{
			showElevationBtn.setSelected(false);
			showRainfallBtn.setSelected(false);
			showFloodsBtn.setSelected(false);
			showWildlifeBtn.setSelected(false);
			reloadImage();
		}
		else if (ev.getSource() == showRainfallBtn)
		{
			showElevationBtn.setSelected(false);
			showTemperatureBtn.setSelected(false);
			showWildlifeBtn.setSelected(false);
			reloadImage();
		}
		else if (ev.getSource() == showFloodsBtn)
		{
			showElevationBtn.setSelected(false);
			showTemperatureBtn.setSelected(false);
			showWildlifeBtn.setSelected(false);
			reloadImage();
		}
		else if (ev.getSource() == showWildlifeBtn)
		{
			showElevationBtn.setSelected(false);
			showTemperatureBtn.setSelected(false);
			showRainfallBtn.setSelected(false);
			showFloodsBtn.setSelected(false);
			reloadImage();
		}
		else if (ev.getSource() == showRiversBtn)
		{
			view.showRivers = showRiversBtn.isSelected();
			reloadImage();
		}
	}

	private void onGenerateTerrainClicked()
	{
		world.generate();
		try
		{
			world.world.saveAll();
		}
		catch (IOException e)
		{
			System.err.println(e.getMessage());
		}
		reloadImage();
	}

	//implements ActionListener
	public void actionPerformed(ActionEvent ev)
	{
		if (toolBtns.containsKey(ev.getActionCommand()))
		{
			onToolSelected(ev.getActionCommand());
		}
		else if (ev.getSource() == expandLakeBtn)
		{
			onExpandLakeClicked();
		}
		else if (ev.getSource() == stepBtn)
		{
			world.doOneStep();
			reloadImage();
			reloadRegionStats();
		}
		else if (ev.getSource() == zoomInBtn)
		{
			view.zoomIn();
		}
		else if (ev.getSource() == zoomOutBtn)
		{
			view.zoomOut();
		}
	}

	int [] RAINFALL_COLORS = {
		0xc2533c, 0xc95c34, 0xd46e2c, 0xde7f23,
		0xe7951d, 0xeca815, 0xf0bd11, 0xf5d40e,
		0xf6ea17, 0xf1f418, 0xb8de14, 0x7ac221,
		0x3ba42a, 0x199332, 0x0d8e35, 0x1c9744,
		0x1e9c65, 0x199980, 0x219592, 0x1a7f90,
		0x186388, 0x1a4780, 0x112d7a
		};

	int [] ELEVATION_COLORS = {
		// below sea-level elevations
		0x000044, 0x000088, 0x0000cc, 0x0000ff,

		// at sea-level and above
		0x008800, 0x00aa00, 0x00cc00, 0x00e000,
		0x00ff00, 0x44ff00, 0x88ff00, 0xcccc00,
		0xddaa00, 0xff9900, 0xff9944, 0xff4444,
		0xffcccc, 0xffffff
		};

	int [] WILDLIFE_COLORS = {
		0, //no wildlife
		0x880000, 0x990000, 0xaa0000, 0xbb0000,
		0xcc0000, 0xdd0000, 0xee0000, 0xff0000,
		0xff1111, 0xff2222, 0xff3333, 0xff4444,
		0xff5555, 0xff6666, 0xff7777, 0xff8888,
		0xff9999, 0xffaaaa, 0xffbbbb, 0xffcccc
		};

	void reloadImage()
	{
		if (world == null)
			return;

		int [] colors = new int[world.g.getCellCount()];
		for (int i = 0; i < colors.length; i++)
		{
			colors[i] = colorOfRegion(i+1);
		}

		view.generateImage(world.g, colors);
	}

	int colorOfRegion(int regionId)
	{
		int i = regionId - 1;

		int el = world.elevation[i];

		if (showTemperatureBtn.isSelected())
		{
			int t = world.temperature[i];
			return t >= 280 ? 0xaa0000 :
				t >= 235 ? 0xdd2200 :
				t >= 180 ? 0xff5500 :
				t >= 122 ? 0xffee00 :
				t >= 80 ? 0x44cc00 :
				t >= 30 ? 0x00ccee :
				t >= -20 ? 0x2233aa :
				0xbb55cc;
		}
		else if (showElevationBtn.isSelected())
		{
			int x = el + 4;
			x = (x >= 0 ? x : 0);
			x = (x < ELEVATION_COLORS.length ? x :
				ELEVATION_COLORS.length - 1);
			return ELEVATION_COLORS[x];
		}
		else if (showRainfallBtn.isSelected() || showFloodsBtn.isSelected())
		{
			boolean showRains = showRainfallBtn.isSelected();
			boolean showFloods = showFloodsBtn.isSelected();

			if (el >= 0)
			{
				int moisture = 0
					+ (showRains ? world.annualRains[i] : 0)
					+ (showFloods ? world.floods[i] * 360 : 0)
					;
				int x = (int)Math.floor(Math.log((double)moisture / MakeWorld.AVERAGE_RAINFALL) * 4) + RAINFALL_COLORS.length / 2;
				if (x < 0)
					x = 0;
				if (x >= RAINFALL_COLORS.length)
					x = RAINFALL_COLORS.length-1;
	
				return RAINFALL_COLORS[x];
			}
			else
			{
				return 0xdddddd;
			}
		}
		else if (showWildlifeBtn.isSelected())
		{
			int wildlife = world.world.regions[i].getWildlifeCount();
			if (wildlife <= 0)
			{
				return 0; //no wildlife
			}

			int x = wildlife < 10 ? 1 :
				(1 + (int)Math.floor(Math.log(wildlife/10)/Math.log(2)));
assert(x >= 1);
			if (x >= WILDLIFE_COLORS.length)
				x = WILDLIFE_COLORS.length-1;
			return WILDLIFE_COLORS[x];
		}
		else
		{
		return 0;

//			RegionServant r = world.regions[i];
//			if (r.biome == BiomeType.LAKE)
//				return 0x6666ff;
//			else if (r.biome.isWater())
//				return 0x0000ff;
//			else
//				return 0x00ff00;
		}
	}

	void reloadRegionStats()
	{
		int regionId = view.selection.selectedRegion;
		if (regionId == 0)
			return;

		RegionServant region = world.world.regions[regionId-1];
		biomeLbl.setText(region.getBiome().name());
		wildlifeLbl.setText(String.format("%d", region.getWildlifeCount()));
		nativesLbl.setText("0");
		elevationLbl.setText(String.format("%d", region.elevation));
		temperatureLbl.setText(String.format("%.1f", region.temperature/10.0));
		moistureLbl.setText(String.format("%d", region.annualRains + region.floods));
		depthLbl.setText(String.format("%d", region.getDepth()));
	}

	//implements WorldView.Listener
	public void onRegionSelected(int regionId)
	{
		assert regionId == view.selection.selectedRegion;
		reloadRegionStats();

		regionPane.setBorder(
			BorderFactory.createTitledBorder("Region "+regionId)
			);
		regionPane.setVisible(true);

		mobPane.setVisible(false);
	}

	//implements WorldView.Listener
	public void onCitySelected(Location cityLocation)
	{
		// not implemented
	}

	//implements WorldView.Listener
	public void onMobSelected(String mobName)
	{
		mobPane.setBorder(
			BorderFactory.createTitledBorder("Mob "+selectedMob)
			);

		MobInfo mob = getSelectedMob();

		mobTypeLbl.setText(mob.hasMobType() ?
			mob.mobType.name().toLowerCase() : "");
		mobOwnerLbl.setText(mob.hasOwner() ?
			mob.owner : "");

		mobPane.setVisible(true);
	}

	private void selectMob(String mobName)
	{
		selectedMob = mobName;
		if (selectedMob != null)
		{
		}
		else
		{
			selectedMob = null;
			mobPane.setVisible(false);
		}
	}

	//implements WorldView.Listener
	public void onTerrainSelected(int regionId, int terrainId)
	{
		onRegionSelected(regionId);
	}

	//implements WorldView.Listener
	public void onEdgeSelected(Geometry.EdgeId edge)
	{
		System.out.println("selected "+edge);
	}

	//implements WorldView.Listener
	public void onVertexSelected(Geometry.VertexId vertex)
	{
		System.out.println("selected "+vertex);
		if (mrivers != null) {
			System.out.println("  elevation: " + mrivers.riverElevation.get(vertex));
		}
	}
}

class MapAdapter extends MapModel
{
	MakeWorld mworld;

	MapAdapter(MakeWorld mworld)
	{
		super(mworld.world.getGeometry());
		this.mworld = mworld;
	}

	public RegionProfile getRegion(int regionId)
	{
		RegionServant realRegion = mworld.world.regions[regionId-1];
		assert realRegion != null : "no servant for region "+regionId;

		RegionProfile r = new RegionProfile();
		r.biome = realRegion.getBiome();
		for (int i = 0; i < 6; i++)
		{
			if (realRegion.sides[i] != null)
				r.sides[i] = realRegion.sides[i].feature;
			if (realRegion.corners[i] != null)
				r.corners[i] = realRegion.corners[i].feature;
		}

		return r;
	}
}

class MobListAdapter extends MobListModel
{
	MakeWorld mworld;

	MobListAdapter(MakeWorld mworld)
	{
		this.mworld = mworld;
		for (RegionServant realRegion : mworld.world.regions)
		{
			for (String mobName : realRegion.presentMobs.keySet())
			{
				mobs.put(mobName, realRegion.presentMobs.get(mobName).makeProfileForOwner());
			}
		}
	}

	
}
