package bigearth;

import java.awt.*;
import javax.swing.*;

public class MainWindow extends JFrame
{
	MapModel map;
	WorldView view;

	public MainWindow()
	{
		super("Big Earth");
		view = new WorldView();
		add(view, BorderLayout.CENTER);

		pack();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setLocationRelativeTo(null);
	}

	public void setMap(MapModel map)
	{
		this.map = map;
		this.view.setMap(map);
	}
}
