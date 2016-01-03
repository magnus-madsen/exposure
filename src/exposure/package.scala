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

import javax.servlet.ServletContext

import better.files.File

import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.resizers.configurations.Antialiasing

import scala.util.Try

package object exposure {

  /**
    * The amount of time before the in-memory database of folders and images is invalidated.
    */
  val CacheTimeout = 5 * 60 * 1000

  /**
    * The default image quality.
    */
  val DefaultImageQuality = 0.85

  /**
    * A list of dimensions (width x height) in which every image is available.
    */
  val MinImageDimensions = List(
    (256, 256),
    (384, 384),
    (512, 512),
    (1024, 1024),
    (2048, 2048),
    (4096, 4096)
  )

  /**
    * Returns the minimum dimension that is larger than the given `minWidth` and `minHeight`.
    */
  def dimensionOf(minWidth: Option[Int], minHeight: Option[Int]): (Int, Int) = {
    val default = MinImageDimensions.last

    (minWidth, minHeight) match {
      case (None, None) => default
      case (Some(w), None) => MinImageDimensions.find(d => d._1 >= w).getOrElse(default)
      case (None, Some(h)) => MinImageDimensions.find(d => d._2 >= h).getOrElse(default)
      case (Some(w), Some(h)) => MinImageDimensions.find(d => d._1 >= w && d._2 >= h).getOrElse(default)
    }
  }

  /**
    * Returns the name of the entire album.
    */
  def albumName(implicit ctx: ServletContext): String = Option(ctx.getInitParameter("albumName")) match {
    case None => throw new IllegalStateException("'albumName' not set in web.xml.")
    case Some(s) => s
  }

  /**
    * Returns the root album path.
    */
  def albumRoot(implicit ctx: ServletContext): File = Option(ctx.getInitParameter("albumRootPath")) match {
    case None => throw new IllegalStateException("'albumRootPath' not set in web.xml.")
    case Some(f) => File(f)
  }

  /**
    * Returns the root cache path.
    */
  def cacheRoot(implicit ctx: ServletContext): File = Option(ctx.getInitParameter("cacheRootPath")) match {
    case None => throw new IllegalStateException("'cacheRootPath' not set in web.xml.")
    case Some(f) => File(f)
  }

  /**
    * Returns the default image quality value.
    */
  def imageQuality(implicit ctx: ServletContext): Double = {
    val value = ctx.getInitParameter("imageQuality")
    if (value == null)
      DefaultImageQuality
    else
      Try(value.toDouble).getOrElse(DefaultImageQuality)
  }

  /**
    * Returns `true` iff the given `file` is an image.
    */
  def isImageFile(file: File): Boolean =
    file.isRegularFile && file.extension.exists(ext => ext.toLowerCase.endsWith(".jpg"))

  /**
    * Returns `true` iff the given `directory` (or a subdirectory) contains an image.
    */
  def isImageFolder(directory: File): Boolean = {
    directory.listRecursively.exists(isImageFile)
  }

  /**
    * Returns the cached album. The album is rebuilt if it has become stale.
    */
  def getAlbum(implicit ctx: ServletContext): Album = {
    // retrieve the album (which may not exist).
    val album = ctx.getAttribute("album").asInstanceOf[Album]

    // (re) build the album if it does not exist or is stale.
    if (album == null || (System.currentTimeMillis() - album.timestamp) > CacheTimeout) {
      ctx.setAttribute("album", buildAlbum(scanRoot))
    }

    // return the album.
    ctx.getAttribute("album").asInstanceOf[Album]
  }

  /**
    * Recursively scans the root album and constructs album folders and images.
    */
  def scanRoot(implicit ctx: ServletContext): AlbumItem.Folder = {

    /**
      * Returns all image files in the given directory `f`.
      */
    def imagesOf(f: File): List[File] =
      f.children.toList.filter(isImageFile).sortBy(_.name)

    /**
      * Returns all image folders in the given directory `f`.
      */
    def foldersOf(f: File): List[File] =
      f.children.toList.filter(isImageFolder).sortBy(_.name)

    /**
      * Visits the given `currentFolder` under the given `currentVirtualPath`.
      */
    def visit(currentFolder: File, currentVirtualPath: String, currentLocation: List[(String, String)]): AlbumItem.Folder = {
      // processes all sub folders in the current folder.
      val folders = foldersOf(currentFolder) map {
        case f =>
          val virtualPath = currentVirtualPath + urlEncode(f.name) + "/"
          val location = currentLocation :::(f.name, virtualPath) :: Nil
          visit(f, virtualPath, location)
      }

      // processes all images in the current folder.
      val images = imagesOf(currentFolder) map {
        case imagePath =>
          val thumbnails = MinImageDimensions map {
            case (w, h) =>
              val relativePath = albumRoot.relativize(imagePath).toString
              (w, h) -> (cacheRoot / relativePath / (w + "x" + h + ".jpg"))

          }

          AlbumItem.Image(imagePath, currentVirtualPath + urlEncode(imagePath.name), thumbnails.toMap)
      }

      AlbumItem.Folder(currentFolder, currentVirtualPath, currentLocation, folders, images)
    }

    visit(currentFolder = albumRoot, currentVirtualPath = "/", currentLocation = Nil)
  }

  /**
    * Returns an album for the given root folder.
    */
  def buildAlbum(root: AlbumItem.Folder)(implicit ctx: ServletContext): Album = {
    // visit every album folder and construct virtual paths.
    def visit(item: AlbumItem): Map[String, AlbumItem] = item match {
      case i: AlbumItem.Image => Map(i.virtualPath -> i)
      case f: AlbumItem.Folder =>
        // visit both sub folders and images in the folder.
        val folders = f.folders.map(visit)
        val images = f.images.map(visit)
        val zero = Map(f.virtualPath -> (f: AlbumItem))
        (folders ::: images).foldLeft(zero) {
          // merge the sub maps.
          case (m1, m2) => m1 ++ m2
        }
    }

    // recursively visit the root folder and construct the map of virtual paths.
    val items = visit(root) + ("/" -> root)

    // return the album with its timestamp.
    Album(albumName, items, System.currentTimeMillis())
  }

  /**
    * Returns an URL friendly representation of the given string.
    */
  def urlEncode(s: String): String =
    (s.toList map {
      case c if c.isLetterOrDigit => c.toLower
      case '.' => '.'
      case '/' => '/'
      case 'æ' => 'a'
      case 'ø' => 'o'
      case 'å' => 'a'
      case _ => '-'
    }).mkString

  /**
    * Resizes the given `inputFile` image to an image of size `width` times `height` that is written to `outputFile`.
    */
  def resizeImage(inputFile: File, outputFile: File, width: Int, height: Int, quality: Double): Unit = {
    // abort if the outputFile already exists.
    if (outputFile.exists) {
      throw new IllegalArgumentException(s"Output Path Exists: $outputFile")
    }

    // create all directories in the path leading up to the outputFile.
    outputFile.parent.createDirectories()

    // create the thumbnail.
    Thumbnails.of(inputFile.toJava)
      .allowOverwrite(false)
      .antialiasing(Antialiasing.ON)
      .width(width)
      .height(height)
      .keepAspectRatio(true)
      .outputQuality(quality)
      .outputFormat("jpg")
      .toFile(outputFile.toJava)
  }

}
