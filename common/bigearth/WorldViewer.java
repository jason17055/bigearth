package bigearth;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.vecmath.*;

public class WorldViewer extends JFrame
	implements ActionListener
{
	public static void main(String [] args)
		throws Exception
	{
		new WorldViewer().setVisible(true);
	}

	WorldView view;
	JButton generateBtn;

	WorldViewer()
	{
		super("World Viewer");
		view = new WorldView();
		add(view, BorderLayout.CENTER);

		generateBtn = new JButton("Generate");
		generateBtn.addActionListener(this);
		add(generateBtn, BorderLayout.SOUTH);

		pack();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
	}

	//implements ActionListener
	public void actionPerformed(ActionEvent ev)
	{
		view.world = new MakeWorld("w1", 20);
		view.world.generate();
		view.repaint();
	}

	class WorldView extends JPanel
	{
		MakeWorld world;

		WorldView()
		{
			setPreferredSize(new Dimension(720,360));
		}

		public void paint(Graphics g)
		{
			Point3d [] pts = new Point3d[world.g.getCellCount()];
			for (int i = 0; i < pts.length; i++)
			{
				pts[i] = world.g.getCenterPoint(i+1);
			}

			for (int y = 0; y < 360; y += 2)
			{
				for (int x = 0; x < 720; x += 2)
				{
					Point3d p = SphereGeometry.fromPolar(
						x * 2*Math.PI/720,
						(180-y) * Math.PI/360
						);
					int cellId = world.g.nearestTo(p);
					if (world.elevation[cellId-1] >= 0)
					{
						g.setColor(Color.GREEN);
					}
					else
					{
						g.setColor(Color.BLUE);
					}
					g.fillRect(x,y,2,2);
				}
			}
		}
	}
}
