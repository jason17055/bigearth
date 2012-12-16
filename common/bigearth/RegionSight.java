package bigearth;

public final class RegionSight
{
	boolean seeInternal;
	boolean seeExternal;

	public static final RegionSight NONE = new RegionSight(false, false);
	public static final RegionSight INTERNAL = new RegionSight(true, true);
	public static final RegionSight EXTERNAL = new RegionSight(false, true);

	public RegionSight(boolean seeInternal, boolean seeExternal)
	{
		this.seeInternal = seeInternal;
		this.seeExternal = seeExternal;
	}

	public boolean equals(Object obj)
	{
		if (obj instanceof RegionSight)
		{
			RegionSight rhs = (RegionSight) obj;
			return this.seeInternal == rhs.seeInternal &&
				this.seeExternal == rhs.seeExternal;
		}
		return false;
	}

	public boolean isNone()
	{
		return seeInternal == false && seeExternal == false;
	}
}
