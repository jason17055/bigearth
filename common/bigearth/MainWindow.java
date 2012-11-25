package bigearth;

import java.awt.*;
import javax.swing.*;
import java.awt.event.*;

public class MainWindow extends JFrame
{
	MapModel map;
	MobListModel mobList;
	WorldView view;

	public MainWindow()
	{
		super("Big Earth");
		view = new WorldView();
		view.zoomFactor = 8;
		add(view, BorderLayout.CENTER);

		JPanel sideBar = new JPanel();
		add(sideBar, BorderLayout.WEST);

		initMenu();

		pack();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setLocationRelativeTo(null);
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

		// find a unit to focus on
		MobInfo [] mobs = mobList.mobs.values().toArray(new MobInfo[0]);
		if (mobs.length != 0)
		{
			MobInfo mob = mobs[0];
			view.panTo(mob.location);
		}
	}
}
