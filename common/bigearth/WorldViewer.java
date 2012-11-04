package bigearth;

import java.io.*;
import java.util.*;
import javax.swing.*;
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
		new WorldViewer().setVisible(true);
	}

	WorldView view;
	JButton generateBtn;
	JCheckBoxMenuItem showElevationBtn;
	JCheckBoxMenuItem showTemperatureBtn;
	JCheckBoxMenuItem showRainfallBtn;
	JCheckBoxMenuItem showFloodsBtn;
	JCheckBoxMenuItem showRiversBtn;
	JButton zoomInBtn;
	JButton zoomOutBtn;

	JPanel toolsPane;
	Map<String, JToggleButton> toolBtns;

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

		JPanel buttonsPane = new JPanel();
		add(buttonsPane, BorderLayout.SOUTH);

		generateBtn = new JButton("Generate");
		generateBtn.addActionListener(this);
		buttonsPane.add(generateBtn);

		zoomInBtn = new JButton("Zoom In");
		zoomInBtn.addActionListener(this);
		buttonsPane.add(zoomInBtn);

		zoomOutBtn = new JButton("Zoom Out");
		zoomOutBtn.addActionListener(this);
		buttonsPane.add(zoomOutBtn);

		initMenu();

		pack();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			public void windowClosed(WindowEvent ev) {
				onWindowClosed();
			}});

		File f = new File("world1");
		if (f.exists())
		{
			setWorld(MakeWorld.load(f));
		}
	}

	void setWorld(MakeWorld newWorld)
	{
		world = newWorld;
		view.world = newWorld;
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
		if (!worldDir.mkdir())
			throw new Exception("Could not create "+worldDir);

		world = new MakeWorld(worldDir, sz);
		reloadImage();

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

		setJMenuBar(menuBar);
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
			reloadImage();
		}
		else if (ev.getSource() == showTemperatureBtn)
		{
			showElevationBtn.setSelected(false);
			showRainfallBtn.setSelected(false);
			showFloodsBtn.setSelected(false);
			reloadImage();
		}
		else if (ev.getSource() == showRainfallBtn)
		{
			showElevationBtn.setSelected(false);
			showTemperatureBtn.setSelected(false);
			reloadImage();
		}
		else if (ev.getSource() == showFloodsBtn)
		{
			showElevationBtn.setSelected(false);
			showTemperatureBtn.setSelected(false);
			reloadImage();
		}
		else if (ev.getSource() == showRiversBtn)
		{
			reloadImage();
		}
	}

	//implements ActionListener
	public void actionPerformed(ActionEvent ev)
	{
		if (toolBtns.containsKey(ev.getActionCommand()))
		{
			onToolSelected(ev.getActionCommand());
		}
		else if (ev.getSource() == generateBtn)
		{
			this.world = new MakeWorld(new File("world1"), 20);
			world.generate();
			try
			{
				world.save();
			}
			catch (IOException e)
			{
				System.err.println(e.getMessage());
			}
			reloadImage();
	
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

	void reloadImage()
	{
		if (world == null)
			return;

		int [] colors = new int[world.g.getCellCount()];
		for (int i = 0; i < colors.length; i++)
		{
			colors[i] = colorOfRegion(i+1);
		}

		view.rivers = new int[world.g.getCellCount()];
		for (int i = 0; i < colors.length; i++)
		{
			if (showRiversBtn.isSelected())
			{
				if (world.riverVolume[i] > 0)
					view.rivers[i] = world.drainage[i];
			}
			else
			{
				view.rivers[i] = 0;
			}
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
		else
		{
			return el < 0 ? 0x0000ff :
				world.lakeLevel[i] > el ? 0x6666ff :
				0x00ff00;
		}
	}

	public void onTerrainClicked(int regionId, int terrainId)
	{
		RegionDetail r = world.regions[regionId-1];
		if (selectedTool.equals("grass"))
		{
			r.setTerrainType(terrainId, TerrainType.GRASS);
		}
		else if (selectedTool.equals("ocean"))
		{
			r.setTerrainType(terrainId, TerrainType.OCEAN);
		}
		else if (selectedTool.equals("lake"))
		{
			r.setTerrainType(terrainId, TerrainType.LAKE);
		}
	}
}
