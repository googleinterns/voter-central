//Copyright 2019 Google LLC
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

// use modern JavaScript (ES5)
"use strict"

window.onload = function() {
  addCommonElements();
};

/**
 * Adds a brief version of the official election/candidate information to the
 * homepage.
 * Queries the backend database with the user input of address.
 *
 * Each election has the following HTML DOM structure:
 *   <div id="election-i">
 *     <h3>Election name</h3>
 *     <time>Election date</time>
 *     <div id="election-1-positions">
 *       <ul>
 *         <li>
 *           <h4>Position 1</h4>
 *           <p>Table of Candidates</p>
 *           <table class="candidates-table">
 *             <tr>
 *               <td><a href="URL">Candidate Name</a></th>
 *               <td>Party Affiliation</th>
 *               <td>Incumbent</th>
 *             </tr>
 *           </table>
 *         </li>
 *         <li>
 *           <h4>Position 2...</h4>
 *         </li>
 *       </ul>
 *     </div>
 *     <hr>
 *   </div>
 */
async function addBriefElectionCandidateInformation() {
  const address = location.search.substring(location.search.indexOf('=') + 1);

  // Send GET request to /data with address.
  const response = await fetch(`/data?address=${address}`);
  const dataPackage = await response.json();
  const elections = dataPackage.electionsData;

  // Add (brief version) official election/candidate information to HTML.
  const electionsContainer = document.getElementById('elections-container');
  electionsContainer.innerHTML = '';
  for (let electionIndex = 0; electionIndex < elections.length;
      electionIndex++) {
    const electionElement = constructElection(elections[electionIndex],
                                              electionIndex);
    electionsContainer.appendChild(electionElement);
  }
}

/**
 * Constructs the HTML of one election, which contains a list of positions.
 */
function constructElection(election, electionIndex) {
  const electionElement = document.createElement('div');
  electionElement.id = `election-${electionIndex + 1}`;
  electionElement.innerHTML =
      `<h3>${election.name}</h3>
        <time>${election.date}</time>`;
  const positions = election.positions;
  const positionsElement = document.createElement('div');
  positionsElement.id = electionElement.id + `-positions`;
  const positionsList = constructPositionsList(positions);
  positionsElement.appendChild(positionsList);
  electionElement.appendChild(positionsElement);
  return electionElement;
}

/**
 * Constructs the HTML of a list of positions, corresponding to one election.
 */
function constructPositionsList(positions) {
  const positionsList = document.createElement('ul');
  for (let positionIndex = 0; positionIndex < positions.length;
      positionIndex++) {
    const positionItem = constructPositionsListItem(positions[positionIndex],
                                                    positionIndex);
    positionsList.appendChild(positionItem);
  }
  return positionsList;
}

/**
 * Constructs the HTML of one position, which contains a table of candidates.
 */
function constructPositionsListItem(position, positionIndex) {
  const positionItem = document.createElement('li');
  positionItem.innerHTML =
      `<h4>Position ${positionIndex + 1}: ${position.name}</h4>
        <p>Table of Candidates</p>`;
  const candidatesTable = constructCandidateTable(position.candidates);
  positionItem.appendChild(candidatesTable);
  return positionItem;
}

/**
 * Constructs the HTML of a table of candidates, corresponding to one position.
 */
function constructCandidateTable(candidates) {
  const candidatesTable = document.createElement('table');
  candidatesTable.innerHTML =
      `<tr>
         <th>Candidate Name</th>
         <th>Party Affiliation</th>
         <th>Incumbent</th>
       </tr>`;
  for (let candidateIndex = 0; candidateIndex < candidates.length;
      candidateIndex++) {
    const candidate = candidates[candidateIndex];
    candidatesTable.innerHTML +=
        constructCandidateTableRow(candidate.id,
                                   candidate.name,
                                   candidate.partyAffiliation,
                                   candidate.incumbency);
  }
  return candidatesTable;
}

/**
 * Constructs the HTML of one table row for a candidate. Adds candidate info
 * and embed candidate ID into the URL.
 */
function constructCandidateTableRow(id, name, partyAffiliation, incumbency) {
  return `<tr>
            <td><a href="candidate.html?candidateId=${id}">
                ${name}</a></td>
            <td>${partyAffiliation}</td>
            <td>${incumbency}</td>
          </tr>`;
}

/**
 * Adds candidate information to the candidate page.
 */
async function addCandidateInformation() {
  const candidateId = location.search.substring(
      location.search.indexOf('=') + 1);

  // Send GET request to /candidate and fetch JSON formatted data for the given
  // candidate ID.
  const response = await fetch(`/candidate?candidateId=${candidateId}`);
  const dataPackage = await response.json();

  // Unpack response.
  const officialCandidateInfo = dataPackage.candidateData;
  const newsArticles = dataPackage.newsArticlesData;
  const socialMedia = dataPackage.socialMediaData;

  // Add 3 components of information to the candidate page.
  addOfficialCandidateInformation(officialCandidateInfo);
  addNewsArticles(newsArticles);
  addSocialMedia(socialMedia);
}

/**
 * @TODO [Adds the official information component to the candidate page.]
 */
function addOfficialCandidateInformation() {}

/**
 * Adds the news articles component to the candidate page.
 *
 * Each piece of news article has the following HTML DOM structure:
 *     <article id="news-article-i">
 *       <h3>News article title</h3>
 *       <a href="URL">Source</a>
 *       <h5>Publisher</h5>
 *       <time>Published date</time>
 *       <div class="news-article-content">
 *         <p>Content [first 100 words]</p>
 *         <a href="URL">Read the full article</a>
 *       </div>
 *       <hr>
 *     </article>
 */
function addNewsArticles(newsArticles) {
  const newsArticlesContainer = document.getElementById('news-articles-container');
  newsArticlesContainer.innerHTML = '';
  for (let i = 0; i < newsArticles.length; i++) {
    const newsArticle = newsArticles[i];
    const articleElement = document.createElement('article');
    articleElement.id = `news-article-${i + 1}`;
    articleElement.innerHTML =
        `<h3>${newsArticle.title}</h3>
         <a href="${newsArticle.url}">Source</a>
         <h5>${newsArticle.publisher}</h5>
         <time>${newsArticle.publishedDate}</time>
         <div class="news-article-content">
           <p>${newsArticle.content}</p>
           <a href="${newsArticle.url}">Read the full article</a>
         </div>
         <hr>`;
    newsArticlesContainer.appendChild(articleElement);
  }
}

/**
 * @TODO [Adds the social media component to the candidate page.]
 */
function addSocialMedia(candidateID) {}
