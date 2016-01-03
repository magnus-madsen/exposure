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
import javax.servlet.annotation.WebServlet
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import org.json4s.JsonAST.{JArray, JInt, JObject, JString}
import org.json4s.native.JsonMethods

/**
  * A servlet for serving JSON to the client.
  */
@WebServlet(urlPatterns = Array("/a/"), loadOnStartup = 1)
class AlbumServlet extends HttpServlet {

  /**
    * Process the HTTP GET request.
    */
  override def doGet(req: HttpServletRequest, res: HttpServletResponse): Unit = {
    // retrieve the current servlet context.
    implicit val ctx = getServletContext

    // retrieve the path parameter.
    val virtualPath = Option(req.getParameter("path")).filter(_.nonEmpty).getOrElse("/")

    // retrieve the album.
    val album = getAlbum

    // retrieve the folder from the album.
    val folder = album.getFolder(virtualPath)

    // compute the folder name.
    val name = if (virtualPath == "/") album.name else folder.path.name

    // construct the JSON response.
    val json = JObject(
      "name" -> JString(name),
      "location" -> locationOf(folder),
      "folders" -> foldersOf(folder),
      "images" -> imagesOf(folder)
    )

    // translate the JSON object into a string.
    val data = JsonMethods.pretty(JsonMethods.render(json))

    // set the response headers and status code.
    res.setHeader("Content-Type", "application/json")
    res.setStatus(HttpServletResponse.SC_OK)

    // write the json data string over the wire.
    res.getOutputStream.write(data.getBytes)
    res.getOutputStream.close()
  }

  /**
    * Returns a JSON Array of the location of the given `folder`.
    */
  private def locationOf(folder: AlbumItem.Folder)(implicit ctx: ServletContext): JArray =
    JArray(folder.location map {
      case (name, path) =>
        JObject(
          "name" -> JString(name),
          "path" -> JString(path)
        )
    })

  /**
    * Returns a JSON Array of the sub folders of the given `folder`.
    */
  private def foldersOf(folder: AlbumItem.Folder)(implicit ctx: ServletContext): JArray =
    JArray(folder.folders map {
      case f =>
        JObject(
          "name" -> JString(f.path.name),
          "path" -> JString(f.virtualPath),
          "time" -> JInt(f.path.lastModifiedTime.getEpochSecond),
          "url" -> JString("i/?path=" + f.coverImage.virtualPath)
        )
    })

  /**
    * Returns a JSON Array of the images in the given `folder`.
    */
  private def imagesOf(folder: AlbumItem.Folder)(implicit ctx: ServletContext): JArray =
    JArray(folder.images map {
      case i =>
        JObject(
          "name" -> JString(i.path.name),
          "url" -> JString("i/?path=" + i.virtualPath)
        )
    })

}
