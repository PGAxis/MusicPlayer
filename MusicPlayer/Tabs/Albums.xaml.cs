namespace MusicPlayer;

public partial class Albums : ContentPage, IResizablePage
{
	private static Albums instance;
	private static object instanceLock = new object();

	public static Albums Instance()
	{
		if (instance == null)
		{
			lock (instanceLock)
			{
				if (instance == null)
				{
					instance = new Albums();
				}
			}
		}
		return instance;
	}

	private Albums()
	{
		InitializeComponent();
        MainStack.WidthRequest = DeviceDisplay.MainDisplayInfo.Width / DeviceDisplay.MainDisplayInfo.Density;
    }

    public void ChangeWidth()
    {
        MainStack.WidthRequest = DeviceDisplay.MainDisplayInfo.Width / DeviceDisplay.MainDisplayInfo.Density;
    }
}