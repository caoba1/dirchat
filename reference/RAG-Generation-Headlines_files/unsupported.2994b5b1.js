"use strict";(self.webpackChunkproton_lumo=self.webpackChunkproton_lumo||[]).push([[4768],{1262:(e,t,o)=>{o.d(t,{y:()=>r});let r=function(e){return e[e.Unsupported=0]="Unsupported",e[e.Supported=1]="Supported",e[e.Other=-1]="Other",e}({})},17231:(e,t,o)=>{e.exports=o.p+"assets/static/error-generic.daeca444fc39d92b060b.svg"},26535:(e,t,o)=>{var r=o(17231),s=o(61395),n=o(1262),p=function(){var e=-1!==(document.location.origin||document.location.href).indexOf("protonvpn"),t=document.location.hostname,o=e?"https://protonvpn.com/support/browsers-supported/":"https://".concat(t.slice(t.indexOf(".")+1),"/support/recommended-browsers");document.body.innerHTML=`
        <div class='h-full flex items-center pb-14 overflow-auto'>
            <div class='m-auto text-center max-w-custom' style='--max-w-custom: 30em'>
                <h1 class='text-bold text-4xl'>Unsupported browser</h1>
                <p>
                    You are using an unsupported browser. Please update it to the latest version or use a different browser.
                </p>
                <a class='primary-link bold' target='_blank' rel='noopener noreferrer' href='`.concat(o,`'>More info</a>
                <div class='mt-8'>
                    <img src='`).concat(s,`' alt='Unsupported browser'/>
                </div>
            </div>
        </div>
    `),document.title="Unsupported browser"},c=function(){var e;document.body.innerHTML=`
        <div class='h-full flex items-center pb-14 overflow-auto'>
            <div class='m-auto text-center max-w-custom' style='--max-w-custom: 30em'>
                <div class='mb-8'>
                    <img src='`.concat(r,`' alt='Error'/>
                </div>
                <h1 class='text-bold text-4xl'>Oops, something went wrong</h1>
                <p>
                    Please <button id='refresh' class='link align-baseline'>refresh the page</button> or try again later.
                </p>
            </div>
        </div>
    `),null==(e=document.querySelector("#refresh"))||e.addEventListener("click",function(){window.location.reload()}),document.title="Oops, something went wrong"};window.setTimeout(function(){window.protonSupportedBrowser===n.y.Unsupported?p():(window.protonSupportedBrowser===n.y.Other||void 0===window.protonSupportedBrowser)&&c()},33)},61395:(e,t,o)=>{e.exports=o.p+"assets/static/unsupported-browser.0e18e3c7785811fb5d8c.svg"}},e=>{e(e.s=26535)}]);
//# sourceMappingURL=unsupported.2994b5b1.js.map