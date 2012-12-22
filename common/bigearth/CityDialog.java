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
	JLabel farmsLbl;
	JLabel housesLbl;
	JLabel pasturesLbl;
	JLabel underConstructionLbl;
	JLabel grainLbl;
	JLabel meatLbl;
	JLabel sheepLbl;
	JLabel pigLbl;
	JComboBox developSelect;
	JComboBox equipSelect;
	DefaultListModel messagesListModel;
	JList messagesList;

	static ZoneType [] developChoices = new ZoneType[] {
		ZoneType.MUD_COTTAGES,
		ZoneType.FARM,
		ZoneType.PASTURE };
	static MobType [] equipChoices = new MobType[] {
		MobType.SETTLER
		};

	void onWindowClosed()
	{
		this.client.removeListener(this.listner);
	}

	CityDialog(Window owner, Client client, Location cityLocation)
	{
		super(owner, "City", Dialog.ModalityType.APPLICATION_MODAL);
		this.client = client;
		this.cityLocation = cityLocation;

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
		mainPane.add(new JLabel("Houses"), c1);
		housesLbl = new JLabel();
		mainPane.add(housesLbl, c2);

		c1.gridy = ++c2.gridy;
		mainPane.add(new JLabel("Pastures"), c1);
		pasturesLbl = new JLabel();
		mainPane.add(pasturesLbl, c2);

		c1.gridy = ++c2.gridy;
		mainPane.add(new JLabel("Farms"), c1);
		farmsLbl = new JLabel();
		mainPane.add(farmsLbl, c2);

		c1.gridy = ++c2.gridy;
		mainPane.add(new JLabel("Under Construction"), c1);
		underConstructionLbl = new JLabel();
		mainPane.add(underConstructionLbl, c2);

		c1.gridy = ++c2.gridy;
		mainPane.add(new JLabel("Grain"), c1);
		grainLbl = new JLabel();
		mainPane.add(grainLbl, c2);

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

		messagesListModel = new DefaultListModel();

		messagesList = new JList(messagesListModel);
		JScrollPane messagesListScroll = new JScrollPane(messagesList);
		messagesListScroll.setPreferredSize(new Dimension(250, 50));
		GridBagConstraints c3 = new GridBagConstraints();
		c3.gridy = c1.gridy = ++c2.gridy;
		c3.gridx = 0;
		c3.gridwidth = 2;
		c3.weighty = c3.weightx = 1.0;
		c3.fill = GridBagConstraints.BOTH;
		mainPane.add(messagesListScroll, c3);

		JPanel buttonPane = new JPanel();
		getContentPane().add(buttonPane, BorderLayout.SOUTH);

		JButton renameBtn = new JButton("Rename City");
		renameBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onRenameClicked();
			}});
		buttonPane.add(renameBtn);

		developSelect = new JComboBox(developChoices);
		buttonPane.add(developSelect);

		JButton developBtn = new JButton("Develop");
		developBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onDevelopClicked();
			}});
		buttonPane.add(developBtn);

		equipSelect = new JComboBox(equipChoices);
		buttonPane.add(equipSelect);

		JButton equipBtn = new JButton("Equip");
		equipBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev)
			{
				onEquipClicked();
			}});
		buttonPane.add(equipBtn);

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

		addWindowListener(new WindowAdapter() {
			public void windowClosed(WindowEvent ev)
			{
				onWindowClosed();
			}
		});

		this.listner = new MyListener();
		this.client.addListener(listner);
		//this.client.mobs.addListener(listner);
	}

	void addCityMessage(String message)
	{
		messagesListModel.add(0, message);
	}

	private void reloadCityInfo()
	{
		try
		{
			CityInfo city = client.getCity(cityLocation);
			if (city == null)
				return;

			loadCityInfo(city);
		}
		catch (IOException e)
		{
			//FIXME
			e.printStackTrace(System.err);
		}
	}

	private void loadCityInfo(CityInfo city)
	{
		nameLbl.setText(city.displayName);
		populationLbl.setText(city.hasPopulation() ?
			Integer.toString(city.population) : null);
		childrenLbl.setText(city.hasChildren() ?
			Integer.toString(city.children) : null);
		housesLbl.setText(city.hasHouses() ?
			Integer.toString(city.houses) : null);
		farmsLbl.setText(city.hasFarms() ?
			Integer.toString(city.farms) : null);
		pasturesLbl.setText(city.hasPastures() ?
			Integer.toString(city.pastures) : null);
		underConstructionLbl.setText(city.hasUnderConstruction() ?
			Integer.toString(city.underConstruction) : null);

		grainLbl.setText(city.hasStock() ?
			Long.toString(city.getStock(CommodityType.GRAIN)) : null);
		meatLbl.setText(city.hasStock() ?
			Long.toString(city.getStock(CommodityType.MEAT)) : null);
		sheepLbl.setText(city.hasStock() ?
			Long.toString(city.getStock(CommodityType.SHEEP)) : null);
		pigLbl.setText(city.hasStock() ?
			Long.toString(city.getStock(CommodityType.PIG)) : null);
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

	private void onEquipClicked()
	{
		MobType type = (MobType) equipSelect.getSelectedItem();
		assert type != null;

		try {

		EquipCommand c = new EquipCommand();
		c.mobType = type;
		client.sendCityOrders(cityLocation, c);

		}
		catch (Exception e)
		{
			e.printStackTrace(System.err);
			JOptionPane.showMessageDialog(this, e,
				"Error",
				JOptionPane.ERROR_MESSAGE);
		}
	}

	private void onDevelopClicked()
	{
		ZoneType type = (ZoneType) developSelect.getSelectedItem();
		assert type != null;

		try {

		DevelopCommand c = new DevelopCommand();
		c.fromZoneType = ZoneType.NATURAL;
		c.toZoneType = type;
		client.sendCityOrders(cityLocation, c);

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
		implements MapModel.Listener, Client.Listener
	{
		//implements MapModel.Listener
		public void regionUpdated(Location loc)
		{
			if (!loc.equals(cityLocation))
				return; //not interested

			SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				reloadCityInfo();
			}
			});
		}

		//implements Client.Listener
		public void cityMessage(Location loc, final String message)
		{
			if (!loc.equals(cityLocation))
				return; //not interested

			SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				addCityMessage(message);
			}
			});
		}

		//implements Client.Listener
		public void cityUpdated(Location loc, final CityInfo cityData)
		{
			if (!loc.equals(cityLocation))
				return; //not interested

			SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				loadCityInfo(cityData);
			}
			});
		}

		//implements Client.Listener
		public void mobMessage(String mobName, String message)
		{
			//not interested
		}
	}
}
