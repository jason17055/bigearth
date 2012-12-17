package bigearth;

import java.io.*;
import java.util.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class CityDialog extends JDialog
{
	Client client;
	Location cityLocation;
	MyListener listner;

	JLabel nameLbl;
	JLabel populationLbl;
	JLabel childrenLbl;;
	JLabel meatLbl;
	JLabel sheepLbl;
	JLabel pigLbl;

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

		GridBagConstraints c1 = new GridBagConstraints();
		c1.gridx = 0;
		c1.anchor = GridBagConstraints.FIRST_LINE_START;
		c1.weightx = 1.0;

		GridBagConstraints c2 = new GridBagConstraints();
		c2.gridx = 1;
		c2.anchor = GridBagConstraints.FIRST_LINE_END;

		c1.gridy = c2.gridy = 0;
		mainPane.add(new JLabel("Name"), c1);
		nameLbl = new JLabel();
		mainPane.add(nameLbl, c2);

		c1.gridy = ++c2.gridy;
		mainPane.add(new JLabel("Population"), c1);
		populationLbl = new JLabel();
		mainPane.add(populationLbl, c2);

		c1.gridy = ++c2.gridy;
		mainPane.add(new JLabel("Children"), c1);
		childrenLbl = new JLabel();
		mainPane.add(childrenLbl, c2);

		c1.gridy = ++c2.gridy;
		mainPane.add(new JLabel("Meat"), c1);
		meatLbl = new JLabel();
		mainPane.add(meatLbl, c2);

		c1.gridy = ++c2.gridy;
		mainPane.add(new JLabel("Sheep"), c1);
		sheepLbl = new JLabel();
		mainPane.add(sheepLbl, c2);

		c1.gridy = ++c2.gridy;
		mainPane.add(new JLabel("Pig"), c1);
		pigLbl = new JLabel();
		mainPane.add(pigLbl, c2);

		JPanel buttonPane = new JPanel();
		getContentPane().add(buttonPane, BorderLayout.SOUTH);

		JButton renameBtn = new JButton("Rename City");
		renameBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onRenameClicked();
			}});
		buttonPane.add(renameBtn);

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
	}

	private void reloadCityInfo()
	{
		try
		{
			CityInfo city = client.getCity(cityLocation);
			if (city == null)
				return;

			nameLbl.setText(city.displayName);
			populationLbl.setText(city.hasPopulation() ?
				Integer.toString(city.population) : null);
			childrenLbl.setText(city.hasChildren() ?
				Integer.toString(city.children) : null);

			meatLbl.setText(city.hasStock() ?
				Long.toString(city.getStock(CommodityType.MEAT)) : null);
			sheepLbl.setText(city.hasStock() ?
				Long.toString(city.getStock(CommodityType.SHEEP)) : null);
			pigLbl.setText(city.hasStock() ?
				Long.toString(city.getStock(CommodityType.PIG)) : null);
		}
		catch (IOException e)
		{
			//FIXME
			e.printStackTrace(System.err);
		}
	}

	private void onCloseClicked()
	{
		dispose();
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
