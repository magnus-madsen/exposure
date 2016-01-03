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

import scala.util.Random

/**
  * An album item is either a folder or an image.
  */
sealed trait AlbumItem

object AlbumItem {

  /**
    * Represents a folder in the album.
    *
    * @param path the path to the folder.
    * @param virtualPath the virtual path to the image.
    * @param folders the folders in the folder.
    * @param images the images in the folder.
    */
  case class Folder(path: File, virtualPath: String, location: List[(String, String)], folders: List[Folder], images: List[Image]) extends AlbumItem {

    /**
      * Returns the cover image of the folder.
      */
    def coverImage(implicit ctx: ServletContext): Image = {
      /**
        * Recursively returns all images of the given folder `f`.
        */
      def listImages(f: Folder): List[Image] = f.images ++ f.folders.flatMap(listImages)

      // select the first image based on the selected strategy.
      val images = listImages(this)
      ctx.getInitParameter("coverImage") match {
        case "byNameAsc" => images.sortBy(_.path.name).head
        case "byNameDesc" => images.sortBy(_.path.name).last
        case "byDateAsc" => images.sortBy(_.path.lastModifiedTime).last
        case "byDateDesc" => images.sortBy(_.path.lastModifiedTime).head
        case "byRandom" => images(Random.nextInt(images.length))
        case _ => images.sortBy(_.path.name).head
      }

    }
  }

  /**
    * Represents an image in the album.
    *
    * @param path the path to the image.
    * @param virtualPath the virtual path to the image.
    * @param thumbnails a map from dimensions to thumbnails.
    */
  case class Image(path: File, virtualPath: String, thumbnails: Map[(Int, Int), File]) extends AlbumItem

}