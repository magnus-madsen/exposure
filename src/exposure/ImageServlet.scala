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

import java.io.{BufferedOutputStream, BufferedInputStream}
import java.util.concurrent.{ExecutorService, Executors}
import javax.servlet.annotation.WebServlet
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import better.files._

import scala.util.Try

/**
  * A servlet for serving scaled images to the client.
  */
@WebServlet(urlPatterns = Array("/i/"), asyncSupported = true, loadOnStartup = 1)
class ImageServlet extends HttpServlet {

  /**
    * A thread pool to used to hold long-running computations when images are resized.
    */
  var pool: ExecutorService = null

  /**
    * Process the HTTP GET request.
    */
  override def doGet(req: HttpServletRequest, res: HttpServletResponse): Unit = {
    // retrieve the current servlet context.
    implicit val ctx = getServletContext

    // retrieves the optional width and optional height.
    val w = Option(req.getParameter("h")).flatMap(w => Try(w.toInt).toOption)
    val h = Option(req.getParameter("w")).flatMap(h => Try(h.toInt).toOption)

    // retrieves the required path.
    val path = req.getParameter("path")
    if (path == null) {
      res.sendError(HttpServletResponse.SC_BAD_REQUEST)
      return
    }

    // starts asynchronous processing of the request.
    val asyncCtx = req.startAsync()

    // submits a task to the pool in which the computation takes places.
    pool.submit(new Runnable {
      override def run(): Unit = try {
        val res = asyncCtx.getResponse.asInstanceOf[HttpServletResponse]

        // find or create the cached resized image.
        getAlbum.getImageWithCache(path, w, h) match {
          case None =>
            // image not found, send an error.
            res.sendError(HttpServletResponse.SC_NOT_FOUND)
          case Some(image) =>
            // image found, send headers and write response.
            res.setStatus(HttpServletResponse.SC_OK)
            // content type
            res.addHeader("Content-Type", "image/jpeg")
            // cache control and expiration
            val oneMonth = 1000 * 60 * 60 * 24 * 31
            res.setDateHeader("Expires", System.currentTimeMillis() + oneMonth)

            // write the response
            for {
              in <- new BufferedInputStream(image.newInputStream).autoClosed
              out <- new BufferedOutputStream(res.getOutputStream).autoClosed
            } in.pipeTo(out)

            res.getOutputStream.close()
        }
      } finally {
        // release resources.
        asyncCtx.complete()
      }
    })
  }

  /**
    * Servlet startup.
    */
  override def init(): Unit = {
    // initialize the executor service.
    pool = Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors())
  }

  /**
    * Servlet shutdown.
    */
  override def destroy(): Unit = {
    // shutdown the executor service.
    pool.shutdownNow()
  }

}
