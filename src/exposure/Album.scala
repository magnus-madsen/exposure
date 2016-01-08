/**
  * Copyright 2016 Magnus Madsen
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

package exposure

import javax.servlet.ServletContext

import better.files.File

/**
  * An album has a name, a map from virtual paths to album items and a timestamp.
  *
  * @param name the name of the album.
  * @param items a map from virtual paths to album items.
  * @param timestamp a timestamp from when the album was loaded into memory.
  */
case class Album(name: String, items: Map[String, AlbumItem], timestamp: Long) {

  /**
    * Returns the folder corresponding to the given `virtualPath` or throws an exception.
    */
  def getFolder(virtualPath: String): AlbumItem.Folder =
    items(virtualPath).asInstanceOf[AlbumItem.Folder]

  /**
    * Optionally returns the thumbnail corresponding to the given `virtualPath`.
    */
  def getImageWithCache(virtualPath: String, minWidth: Option[Int], minHeight: Option[Int])(implicit ctx: ServletContext): Option[File] = {
    // lookup the virtual path.
    items.get(virtualPath) flatMap {
      case image: AlbumItem.Image =>
        // lookup the dimensions.
        val (w, h) = dimensionOf(minWidth, minHeight)

        // the thumbnail file.
        val thumbnail = image.thumbnails((w, h))

        // use the highest quality if the thumbnail is small.
        val quality = if (w * h <= 400 * 400) 1.0 else imageQuality

        // check if the thumbnail exists, otherwise create it.
        if (!thumbnail.exists) {
          resizeImage(image.path, thumbnail, w, h, quality)
        }

        Some(thumbnail)
      case _ => None
    }
  }
}