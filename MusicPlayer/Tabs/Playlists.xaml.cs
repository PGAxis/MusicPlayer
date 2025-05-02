namespace MusicPlayer;

public partial class Playlists : ContentPage
{
	private static Playlists instance;
	private static object instanceLock = new object();

	public static Playlists Instance()
	{
		if (instance == null)
		{
			lock (instanceLock)
			{
				if (instance == null)
				{
					instance = new Playlists();
				}
			}
		}
		return instance;
	}

	private Playlists()
	{
		InitializeComponent();
        MainStack.WidthRequest = (DeviceDisplay.MainDisplayInfo.Width) / (DeviceDisplay.MainDisplayInfo.Density);
    }
}