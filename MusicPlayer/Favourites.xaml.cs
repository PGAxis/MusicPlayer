namespace MusicPlayer;

public partial class Favourites : ContentPage
{
	public Favourites()
	{
		InitializeComponent();
		FavouritesLabel.WidthRequest = (DeviceDisplay.MainDisplayInfo.Width)/(DeviceDisplay.MainDisplayInfo.Density);
		FavouritesLabel.Text = DeviceDisplay.MainDisplayInfo.Width.ToString();
	}
}