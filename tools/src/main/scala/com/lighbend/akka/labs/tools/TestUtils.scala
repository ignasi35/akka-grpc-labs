package com.lighbend.akka.labs.tools

import java.io.{ BufferedInputStream, BufferedOutputStream, File, FileOutputStream }

object TestUtils {

  def loadCert(name: String): File = {
    val in = new BufferedInputStream(classOf[PlaceHolder].getResourceAsStream("/certs/" + name))
    val tmpFile: File = File.createTempFile(name, "")
    tmpFile.deleteOnExit()
    val os = new BufferedOutputStream(new FileOutputStream(tmpFile))
    try {
      var b = 0
      do {
        b = in.read
        if (b != -1)
          os.write(b)
        os.flush()
      } while (b != -1)
    } finally {
      in.close()
      os.close()
    }
    tmpFile
  }

}
