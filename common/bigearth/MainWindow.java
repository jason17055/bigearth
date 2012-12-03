package bigearth;

import java.awt.*;
import javax.swing.*;
import java.awt.event.*;

public class MainWindow extends JFrame
	implements Client.Listener
{
	MapModel map;
	MobListModel mobList;
	WorldView view;
	Client client;
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
			public void onRegionSelected(int regionId)
			{
				selectMobAt(regionId);
			}

			};
		view.zoomFactor = 8;
		add(view, BorderLayout.CENTER);

		JPanel sideBar = new JPanel();
		setSideBarDimensions(sideBar);
		add(sideBar, BorderLayout.WEST);

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

		menuItem = new JMenuItem("Build City");
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				onBuildCityClicked();
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
		this.view.setMobs(mobList);

		// find a unit to focus on
		MobInfo [] mobs = mobList.mobs.values().toArray(new MobInfo[0]);
		if (mobs.length != 0)
		{
			MobInfo mob = mobs[0];
			view.panTo(mob.location);
		}
	}

	void selectMobAt(int regionId)
	{
		Location loc = new SimpleLocation(regionId);
		for (MobInfo mob : mobList.mobs.values())
		{
			if (mob.location.equals(loc))
			{
				selectMob(mob.name);
				return;
			}
		}
	}

	void selectMob(String mobName)
	{
		System.out.println("selecting "+mobName);
		view.selection.selectMob(mobName);
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

	void onBuildCityClicked()
	{
		if (!view.selection.isMob())
			return;

		try
		{
			client.setMobActivity(view.selection.getMob(),
				"build-city");
		}
		catch (Exception e)
		{
			e.printStackTrace(System.err);

			JOptionPane.showMessageDialog(this, e,
				"Error",
				JOptionPane.ERROR_MESSAGE);
		}
	}
}
