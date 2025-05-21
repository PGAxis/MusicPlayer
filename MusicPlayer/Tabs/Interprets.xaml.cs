namespace MusicPlayer;

public partial class Interprets : ContentPage, IResizablePage
{
	private static Interprets instance;
	private static object instanceLock = new object();

	public static Interprets Instance()
	{
		if (instance == null)
		{
			lock (instanceLock)
			{
				if (instance == null)
				{
					instance = new Interprets();
				}
			}
		}
		return instance;
	}

	private Interprets()
	{
		InitializeComponent();
        MainStack.WidthRequest = DeviceDisplay.MainDisplayInfo.Width / DeviceDisplay.MainDisplayInfo.Density;
    }

    public void ChangeWidth()
    {
        MainStack.WidthRequest = DeviceDisplay.MainDisplayInfo.Width / DeviceDisplay.MainDisplayInfo.Density;
    }
}