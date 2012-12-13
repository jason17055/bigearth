package bigearth;

import java.util.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class CityDialog extends JDialog
{
	Client client;
	Location cityLocation;
	MyListener listner;

	CityDialog(Window owner, Client client, Location cityLocation)
	{
		super(owner, "City", Dialog.ModalityType.APPLICATION_MODAL);
		this.client = client;
		this.cityLocation = cityLocation;
		this.listner = new MyListener();

		//this.client.map.addListener(listner);
		//this.client.mobs.addListener(listner);

		JPanel mainPane = new JPanel(new GridBagLayout());
		getContentPane().add(mainPane, BorderLayout.CENTER);

		JPanel buttonPane = new JPanel();
		getContentPane().add(buttonPane, BorderLayout.SOUTH);

		JButton refreshBtn = new JButton("Refresh");
		refreshBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onRefreshClicked();
			}});
		buttonPane.add(refreshBtn);

		JButton closeBtn = new JButton("Close");
		closeBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onCloseClicked();
			}});
		buttonPane.add(closeBtn);

		pack();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setLocationRelativeTo(owner);
	}

	private void onCloseClicked()
	{
		dispose();
	}

	private void onRefreshClicked()
	{
	}

	private class MyListener
		implements MapModel.Listener
	{
		//implements MapModel.Listener
		public void regionUpdated(Location loc)
		{
			if (!loc.equals(cityLocation))
				return; //not interested

			// do something
		}
	}
}
