/*
 * Copyright Myrrix Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.myrrix.web.servlets;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;

import net.myrrix.common.MyrrixRecommender;
import net.myrrix.common.NotReadyException;
import net.myrrix.online.RescorerProvider;

/**
 * <p>Responds to a GET request to
 * {@code /recommend/[userID](?howMany=n)(&considerKnownItems=true|false)(&rescorerParams=...)}
 * and in turn calls
 * {@link MyrrixRecommender#recommend(long, int)}. If {@code howMany} is not specified, defaults to
 * {@link AbstractMyrrixServlet#DEFAULT_HOW_MANY}. If {@code considerKnownItems} is not specified,
 * it is considered {@code false}.</p>
 *
 * <p>Output is in CSV or JSON format. Select output format using the HTTP {@code Accept} header.</p>
 *
 * <p>CSV output contains one recommendation per line, and each line is of the form {@code itemID, strength},
 * like {@code 325, 0.53}. Strength is an opaque indicator of the relative quality of the recommendation.</p>
 *
 * <p>JSON output is an array of arrays, with each sub-array containing an item ID and strength.
 * Example: {@code [[325, 0.53], [98, 0.499]]}.</p>
 *
 * @author Sean Owen
 */
public final class RecommendServlet extends AbstractMyrrixServlet {
  
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

    String pathInfo = request.getPathInfo();
    if (pathInfo == null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No path");      
    }
    Iterator<String> pathComponents = SLASH.split(pathInfo).iterator();
    long userID;
    try {
      userID = Long.parseLong(pathComponents.next());
    } catch (NoSuchElementException nsee) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, nsee.toString());
      return;
    } catch (NumberFormatException nfe) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, nfe.toString());
      return;
    }
    if (pathComponents.hasNext()) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Path too long");
      return;
    }

    MyrrixRecommender recommender = getRecommender();
    RescorerProvider rescorerProvider = getRescorerProvider();
    try {
      IDRescorer rescorer = rescorerProvider == null ? null :
          rescorerProvider.getRecommendRescorer(new long[] {userID}, recommender, getRescorerParams(request));
      List<RecommendedItem> recommended =
          recommender.recommend(userID, getHowMany(request), getConsiderKnownItems(request), rescorer);
      output(request, response, recommended);
    } catch (NoSuchUserException nsue) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, nsue.toString());
    } catch (NotReadyException nre) {
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, nre.toString());
    } catch (TasteException te) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, te.toString());
      getServletContext().log("Unexpected error in " + getClass().getSimpleName(), te);
    } catch (IllegalArgumentException iae) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, iae.toString());
    } catch (UnsupportedOperationException uoe) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, uoe.toString());
    }
  }

  @Override
  protected Long getUnnormalizedPartitionToServe(HttpServletRequest request) {
    String pathInfo = request.getPathInfo();
    if (pathInfo == null) {
      return null;
    }
    Iterator<String> pathComponents = SLASH.split(pathInfo).iterator();
    long userID;
    try {
      userID = Long.parseLong(pathComponents.next());
    } catch (NoSuchElementException ignored) {
      return null;
    } catch (NumberFormatException ignored) {
      return null;
    }
    return userID;
  }

}
