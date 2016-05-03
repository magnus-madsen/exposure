/**
 * Copyright 2015 Magnus Madsen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * The URL to retrieve JSON data from.
 */
var URL = "http://" + window.location.host + window.location.pathname + "/a/?path=";

/**
 * Returns the path corresponding to the hash location.
 */
function getPathFromHash() {
    var hash = window.location.hash;
    if (hash) {
        var folders = hash.substr(1).split("/");
        var last = _.last(folders);
        if (_.endsWith(last, ".jpg")) {
            return _.dropRight(folders, 1).join("/") + '/'
        }
        return folders.join("/");
    }
    return "/"
}

/**
 * Returns the image name corresponding to the hash location.
 */
function getImageFromHash() {
    var hash = window.location.hash;
    if (hash) {
        var folders = hash.substr(1).split("/");
        var last = _.last(folders);
        if (_.endsWith(last.toLowerCase(), ".jpg")) {
            return last;
        }
    }
    return null;
}

/**
 * The Application.
 */
var App = React.createClass({

    mixins: [TimerMixin],

    getInitialState: function () {
        return {
            path: getPathFromHash(),
            location: [],
            name: "Loading...",
            folders: [],
            images: [],
            currentImage: null
        }
    },

    componentDidMount: function () {
        this.refresh(this.state.path);

        window.onpopstate = () => {
            var path = getPathFromHash();
            this.refresh(path);
        }
    },

    componentWillUnmount: function () {
        window.onpopstate = null;
    },

    /**
     * Retrieves album information about the given path.
     */
    refresh: function (path) {
        $.ajax({url: URL + path}).done(data => {
            var name = getImageFromHash();
            var image = null;
            if (name != null) {
                image = data.images.find((i) => i.name == name);
            }

            this.setState({
                path: path,
                name: data.name,
                location: data.location,
                folders: data.folders,
                images: data.images,
                currentImage: image
            });
        });
    },

    /**
     * Display the the given path as an album.
     */
    clickPath: function (path) {
        if (typeof path !== "string") {
            throw new Error("Illegal argument 'path'. Must be a string.");
        }

        this.clickFolder(path);
    },

    /**
     * Displays the lightbox for the given image.
     */
    clickImage: function (name) {
        if (typeof name !== "string") {
            throw new Error("Illegal argument 'name'. Must be a string.");
        }

        // find the image given its name.
        var image = this.state.images.find((i) => i.name == name);

        // update the hash to reflect the clicked image.
        window.history.pushState(null, null, '#' + this.state.path + image.name);

        // update the state to point to the clicked image.
        this.setState({currentImage: image});

        // preload the next image after 3 seconds.
        this.setTimeout(() => this.preload(this.nextImage()), 3000)
    },

    /**
     * Display the the given path as an album.
     */
    clickFolder: function (path) {
        if (typeof path !== "string") {
            throw new Error("Illegal argument 'path'. Must be a string.");
        }

        this.setState({
            folders: [],
            images: []
        });
        window.history.pushState(null, null, '#' + path);
        this.refresh(path);
    },

    /**
     * Returns the prev image.
     */
    prevImage: function () {
        var images = this.state.images;
        var currentImage = this.state.currentImage;
        var currentIndex = images.indexOf(currentImage);
        var prevIndex = (currentIndex > 0) ? (currentIndex - 1) : (images.length - 1);
        return images[prevIndex];
    },

    /**
     * Returns the next image.
     */
    nextImage: function () {
        var images = this.state.images;
        var currentImage = this.state.currentImage;
        var currentIndex = images.indexOf(currentImage);
        var nextIndex = (currentIndex + 1) % images.length;
        return images[nextIndex];
    },

    /**
     * Hides the lightbox and shows the album.
     */
    clickEsc: function () {
        window.history.pushState(null, null, '#' + this.state.path);
        this.setState({currentImage: null});
    },

    /**
     * Moves the lightbox to the previous image.
     */
    clickPrev: function () {
        var prevImage = this.prevImage();
        window.history.pushState(null, null, '#' + this.state.path + prevImage.name);
        this.setState({currentImage: prevImage});
    },

    /**
     * Moves the lightbox to the next image.
     */
    clickNext: function () {
        var nextImage = this.nextImage();
        window.history.pushState(null, null, '#' + this.state.path + nextImage.name);
        this.setState({currentImage: nextImage});

        // preload the next image after 3 seconds.
        this.setTimeout(() => this.preload(this.nextImage()), 3000)
    },

    /**
     * Preloads the given image.
     */
    preload: function (image) {
        var width = window.screen.width;
        var height = window.screen.height;
        var url = "img" + "/" + width + "x" + height + image.url;
        var img = new Image(width, height);
        console.log("Preloading: '" + url + "'.");
        img.onload = () => {
            console.log("Image: '" + url + "' preloaded.")
        };
        img.src = url;
    },

    render: function () {
        if (this.state.currentImage) {
            var currentImage = this.state.currentImage;
            return <Lightbox
                name={currentImage.name}
                url={currentImage.url}
                time={currentImage.time}
                clickEsc={this.clickEsc}
                clickPrev={this.clickPrev}
                clickNext={this.clickNext}
            />
        }
        else return (<Album name={this.state.name}
                            location={this.state.location}
                            folders={this.state.folders}
                            images={this.state.images}
                            clickPath={this.clickPath}
                            clickFolder={this.clickFolder}
                            clickImage={this.clickImage}/>);
    }

});


/**
 * An album is a collection of folders and images.
 */
var Album = React.createClass({

    propTypes: {
        name: React.PropTypes.string.isRequired,
        location: React.PropTypes.array.isRequired,
        folders: React.PropTypes.array.isRequired,
        images: React.PropTypes.array.isRequired,
        clickPath: React.PropTypes.func.isRequired,
        clickFolder: React.PropTypes.func.isRequired,
        clickImage: React.PropTypes.func.isRequired
    },

    render: function () {
        document.title = this.props.name;

        return (
            <div className="album">
                <div className="album-name">{this.props.name}</div>

                <Breadcrumb location={this.props.location} clickFolder={this.props.clickFolder}/>

                <div className="album-items">
                    {this.props.folders.map(item =>
                        <Folder key={item.url}
                                name={item.name}
                                path={item.path}
                                time={item.time}
                                url={item.url}
                                clickFolder={this.props.clickFolder}/>
                    )}

                    {this.props.images.map(item =>
                        <Image key={item.url}
                               name={item.name}
                               url={item.url}
                               clickImage={this.props.clickImage}/>
                    )}
                </div>
            </div>
        );
    }
});

/**
 * A component that display the breadcrumb for the current album.
 */
var Breadcrumb = React.createClass({

    propTypes: {
        location: React.PropTypes.array.isRequired,
        clickFolder: React.PropTypes.func.isRequired
    },

    render: function () {
        return (
            <div className="breadcrumb">
                <span className="fragment">
                    <a href="#" onClick={() => this.props.clickFolder("/")}>Home</a>
                </span>

                {this.props.location.map((fragment, index) => {
                    var name = fragment.name;
                    var path = fragment.path;
                    return (
                        <span key={index}>
                            <span className="fa fa-angle-right spacer"/>
                            <span className="fragment">
                                <a href="#" onClick={(e) => {
                                    e.preventDefault();
                                    this.props.clickFolder(path);
                                }}>{name}</a>
                            </span>
                        </span>
                    );
                })}
            </div>

        );
    }
});

/**
 * A component that represents a clickable folder.
 */
var Folder = React.createClass({

    propTypes: {
        name: React.PropTypes.string.isRequired,
        path: React.PropTypes.string.isRequired,
        time: React.PropTypes.number.isRequired,
        url: React.PropTypes.string.isRequired,
        clickFolder: React.PropTypes.func.isRequired
    },

    getFormattedTime: function () {
        var time = moment.unix(this.props.time);
        return time.format('MMMM YYYY');
    },

    render: function () {
        var width = Math.round(0.15 * window.screen.width);
        var height = Math.round(0.15 * window.screen.height);
        var url = "img" + "/" + width + "x" + height + this.props.url;

        var style = {
            "backgroundImage": "url('" + url + "')"
        };

        return (
            <div className="folder-item" style={style} onClick={() => this.props.clickFolder(this.props.path)}>
                <div className="folder-text">
                    <div className="folder-name">{this.props.name}</div>
                    <div className="folder-date">{this.getFormattedTime()}</div>
                </div>
            </div>
        )
    }

});

/**
 * A component that represents a clickable image.
 */
var Image = React.createClass({

    propTypes: {
        name: React.PropTypes.string.isRequired,
        url: React.PropTypes.string.isRequired,
        clickImage: React.PropTypes.func.isRequired
    },

    render: function () {
        var width = Math.round(0.15 * window.screen.width);
        var height = Math.round(0.15 * window.screen.height);
        var url = "img" + "/" + width + "x" + height + this.props.url;

        return (
            <div className="image-item" onClick={() => this.props.clickImage(this.props.name)}>
                <img src={url} className="image"/>
            </div>
        );

    }
});

/**
 * A lightbox is a component for showing an image in full screen together with previous/next image navigation buttons.
 */
var Lightbox = React.createClass({

    propTypes: {
        name: React.PropTypes.string.isRequired,
        url: React.PropTypes.string.isRequired,
        clickEsc: React.PropTypes.func.isRequired,
        clickPrev: React.PropTypes.func.isRequired,
        clickNext: React.PropTypes.func.isRequired
    },

    /**
     * Returns a function that when called starts a slideshow with the given delay.
     */
    slideshow: function (delay) {
        if (this.timers === undefined) {
            this.timers = [];
        }
        return () => {
            var id = window.setInterval(this.props.clickNext, delay * 1000);
            this.timers.push(id);
        };
    },

    /**
     * Register keyboard listeners.
     */
    componentDidMount: function () {
        Mousetrap.bind(['esc', 'home'], this.props.clickEsc);
        Mousetrap.bind(['left', 'up', 'pageup'], this.props.clickPrev);
        Mousetrap.bind(['right', 'down', 'pagedown', 'space'], this.props.clickNext);

        for (var i = 1; i < 10; i++) {
            Mousetrap.bind([String(i)], this.slideshow(i));
        }
    },

    /**
     * Unregisters keyboard listeners.
     */
    componentWillUnmount: function () {
        Mousetrap.unbind(['esc', 'home'], this.props.clickEsc);
        Mousetrap.unbind(['left', 'up', 'pageup'], this.props.clickPrev);
        Mousetrap.unbind(['right', 'down', 'pagedown', 'space'], this.props.clickNext);

        this.timers.forEach(id => window.clearTimeout(id));
    },

    /**
     * Renders the lightbox.
     */
    render: function () {
        var width = window.screen.width;
        var height = window.screen.height;
        var url = "img" + "/" + width + "x" + height + this.props.url;

        return (
            <div className="lightbox">
                <div className="lightbox-prev" onClick={this.props.clickPrev}>
                    <div className="fa fa-chevron-left fa-3x left-arrow"></div>
                </div>
                <div className="lightbox-container" onClick={this.props.clickEsc}>
                    <img src={url} onClick={(e) => {
                        // stop propagation to ensure that we don't
                        // accidentally trigger clickEsc.
                        e.stopPropagation();
                        this.props.clickNext();
                        }}/>
                </div>
                <div className="lightbox-next" onClick={this.props.clickNext}>
                    <div className="fa fa-chevron-right fa-3x right-arrow"></div>
                </div>
            </div>
        );
    }

});

/**
 * Load Application once the document is ready.
 */
$(document).ready(() => {
    React.render(<App />, document.getElementById('app'));
});
