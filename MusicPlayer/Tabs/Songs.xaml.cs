namespace MusicPlayer;

public partial class Songs : ContentPage
{
	private static Songs instance;
	private static object instanceLock = new object();

	public static Songs Instance()
	{
		if (instance == null)
		{
			lock (instanceLock)
			{
				if (instance == null)
				{
					instance = new Songs();
				}
			}
		}
		return instance;
	}

	private Songs()
	{
		InitializeComponent();
        MainStack.WidthRequest = DeviceDisplay.MainDisplayInfo.Width / DeviceDisplay.MainDisplayInfo.Density;
    }
}