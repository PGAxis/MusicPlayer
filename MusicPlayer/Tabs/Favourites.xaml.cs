namespace MusicPlayer;

public partial class Favourites : ContentPage, IResizablePage
{
	private static Favourites instance;
	private static object instanceLock = new object();

	public static Favourites Instance()
	{
		if (instance == null)
		{
			lock (instanceLock)
			{
				if (instance == null)
				{
					instance = new Favourites();
				}
			}
		}
		return instance;
	}

	private Favourites()
	{
		InitializeComponent();
        MainStack.WidthRequest = DeviceDisplay.MainDisplayInfo.Width / DeviceDisplay.MainDisplayInfo.Density;
	}

	public void ChangeWidth()
	{
        MainStack.WidthRequest = DeviceDisplay.MainDisplayInfo.Width / DeviceDisplay.MainDisplayInfo.Density;
    }
}