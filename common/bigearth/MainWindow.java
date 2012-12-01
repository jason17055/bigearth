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
	String selectedMob;
	Client client;

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
		selectMob(null);
	}

	void selectMob(String mobName)
	{
		System.out.println("selected "+mobName);
		selectedMob = mobName;
	}

	void moveMobTo(int regionId)
	{
		Location loc = new SimpleLocation(regionId);

		System.out.println("want to move "+selectedMob+" to "+loc);
		try
	{
		client.moveMobTo(selectedMob, loc);
		}
		catch (Exception e)
		{
			System.out.println(e);
		}
	}
}
