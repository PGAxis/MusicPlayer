using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MusicPlayer
{
    public class PlaylistWithSongs
    {
        public string Title { get; set; }
        public string PlaylistCover { get; set; } = "default_cover.png";
        public List<Song> Songs { get; set; } = new ();
    }
}
