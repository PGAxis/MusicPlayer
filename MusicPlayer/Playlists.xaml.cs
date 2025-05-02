using static System.Net.Mime.MediaTypeNames;

namespace MusicPlayer;

public partial class Playlists : ContentPage
{
	public Playlists()
	{
		InitializeComponent();
        MainStack.WidthRequest = (DeviceDisplay.MainDisplayInfo.Width) / (DeviceDisplay.MainDisplayInfo.Density);
        PlaylistsLabel.Text = DeviceDisplay.MainDisplayInfo.Width.ToString();
    }
}