using System.Collections.ObjectModel;

namespace MusicPlayer
{
    public partial class MainPage : ContentPage
    {
        public ObservableCollection<TabItem> TabItems { get; set; } = new();
        public TabItem SelectedTab { get; set; }

        public MainPage()
        {
            InitializeComponent();
            BindingContext = this;

            TabItems.Add(new TabItem("Page 1", new Favourites()));
            /*TabItems.Add(new TabItem("Page 2", new Page2()));
            TabItems.Add(new TabItem("Page 3", new Page3()));
            TabItems.Add(new TabItem("Page 4", new Page4()));
            TabItems.Add(new TabItem("Page 5", new Page5()));
            TabItems.Add(new TabItem("Page 6", new Page6()));*/

            SelectedTab = TabItems.First();
            

            TabBar.SelectionChanged += (s, e) => UpdateContent();
            UpdateContent();
        }

        private void UpdateContent()
        {
            TabContentView.Content = SelectedTab?.Content.Content;
        }
    }

    public class TabItem
    {
        public string Title { get; set; }
        public ContentPage Content { get; set; }

        public TabItem(string title, ContentPage content)
        {
            Title = title;
            Content = content;
        }
    }
}
