// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.sps.servlets;

import com.google.sps.data.Candidate;
import com.google.sps.data.NewsArticle;
import java.io.IOException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/candidateServlet")
public class CandidateServlet extends HttpServlet {

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // Extracts candidate ID.

    // Gets (1) official election/candidate information.

    // Gets (2) news article information.

    // Gets (3) social media feed (this may not be necessary).

    // Sends data as response.

  }

  // Function for getting (1) official election/candidate information from the database.

  // Function for getting (2) news article information from the database.

  // Function for getting (3) social media information from the database (this may not be
  // necessary).

  // to package together different types of data as a HTTP response.
  class CandidateDataPackage {
    private Candidate candidate;
    private NewsArticle[] newsArticles;
  }
}
