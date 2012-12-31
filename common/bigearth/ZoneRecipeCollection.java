package bigearth;

import java.io.*;
import java.util.*;
import com.fasterxml.jackson.core.*;

public class ZoneRecipeCollection
{
	static final String RESOURCE_NAME = "/zone-recipes.txt";
	static final class MyKey
	{
		ZoneType fromZoneType;
		ZoneType toZoneType;

		public MyKey(ZoneType fromZoneType, ZoneType toZoneType)
		{
			assert fromZoneType != null;
			assert toZoneType != null;

			this.fromZoneType = fromZoneType;
			this.toZoneType = toZoneType;
		}

		public boolean equals(Object obj)
		{
			if (obj instanceof MyKey)
			{
				MyKey rhs = (MyKey) obj;
				return this.fromZoneType == rhs.fromZoneType
				&& this.toZoneType == rhs.toZoneType;
			}
			return false;
		}

		public int hashCode()
		{
			return fromZoneType.hashCode() * 33
			+ toZoneType.hashCode();
		}
	}

	Map<MyKey, ZoneRecipe> recipes = new HashMap<MyKey, ZoneRecipe>();

	private ZoneRecipeCollection()
	{
	}

	public static ZoneRecipeCollection load(WorldConfigIfc world)
		throws IOException
	{
		InputStream inStream = ZoneRecipeCollection.class.getResourceAsStream(RESOURCE_NAME);
		if (inStream == null)
			throw new IOException(RESOURCE_NAME + ": file not found");

		JsonParser in = new JsonFactory().createJsonParser(inStream);
		ZoneRecipeCollection me = new ZoneRecipeCollection();
		me.parse(in, world);
		in.close();

		return me;
	}

	private void parse(JsonParser in, WorldConfigIfc world)
		throws IOException
	{
		in.nextToken();
		if (in.getCurrentToken() != JsonToken.START_ARRAY)
			throw new InputMismatchException();

		while (in.nextToken() != JsonToken.END_ARRAY)
		{
			ZoneRecipe r = ZoneRecipe.parse1(in, world);
			addRecipe(r);
		}
	}

	void addRecipe(ZoneRecipe recipe)
	{
		MyKey k = new MyKey(recipe.fromZoneType, recipe.toZoneType);
		recipes.put(k, recipe);
	}

	public ZoneRecipe get(ZoneType fromZoneType, ZoneType toZoneType)
	{
		MyKey k = new MyKey(fromZoneType, toZoneType);
		return recipes.get(k);
	}

	public Collection<ZoneRecipe> values()
	{
		return recipes.values();
	}
}
