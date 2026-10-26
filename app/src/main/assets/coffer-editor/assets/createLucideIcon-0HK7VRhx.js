import{createContext as A,createElement as n,forwardRef as d,useContext as b}from"react";/**
* @license lucide-react v1.24.0 - ISC
*
* This source code is licensed under the ISC license.
* See the LICENSE file in the root directory of this source tree.
*/var C=(...t)=>t.filter((e,r,o)=>!!e&&e.trim()!==""&&o.indexOf(e)===r).join(" ").trim();/**
* @license lucide-react v1.24.0 - ISC
*
* This source code is licensed under the ISC license.
* See the LICENSE file in the root directory of this source tree.
*/var L=t=>t.replace(/([a-z0-9])([A-Z])/g,"$1-$2").toLowerCase();/**
* @license lucide-react v1.24.0 - ISC
*
* This source code is licensed under the ISC license.
* See the LICENSE file in the root directory of this source tree.
*/var W=t=>t.replace(/^([A-Z])|[\s-_]+(\w)/g,(e,r,o)=>o?o.toUpperCase():r.toLowerCase());/**
* @license lucide-react v1.24.0 - ISC
*
* This source code is licensed under the ISC license.
* See the LICENSE file in the root directory of this source tree.
*/var h=t=>{const e=W(t);return e.charAt(0).toUpperCase()+e.slice(1)};/**
* @license lucide-react v1.24.0 - ISC
*
* This source code is licensed under the ISC license.
* See the LICENSE file in the root directory of this source tree.
*/var c={xmlns:"http://www.w3.org/2000/svg",width:24,height:24,viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:2,strokeLinecap:"round",strokeLinejoin:"round"};/**
* @license lucide-react v1.24.0 - ISC
*
* This source code is licensed under the ISC license.
* See the LICENSE file in the root directory of this source tree.
*/var S=t=>{for(const e in t)if(e.startsWith("aria-")||e==="role"||e==="title")return!0;return!1};/**
* @license lucide-react v1.24.0 - ISC
*
* This source code is licensed under the ISC license.
* See the LICENSE file in the root directory of this source tree.
*/var y=A({}),$=()=>b(y);/**
* @license lucide-react v1.24.0 - ISC
*
* This source code is licensed under the ISC license.
* See the LICENSE file in the root directory of this source tree.
*/var j=d(({color:t,size:e,strokeWidth:r,absoluteStrokeWidth:o,className:s="",children:a,iconNode:p,...l},m)=>{const{size:i=24,strokeWidth:u=2,absoluteStrokeWidth:v=!1,color:x="currentColor",className:w=""}=$()??{},f=o??v?Number(r??u)*24/Number(e??i):r??u;return n("svg",{ref:m,...c,width:e??i??c.width,height:e??i??c.height,stroke:t??x,strokeWidth:f,className:C("lucide",w,s),...!a&&!S(l)&&{"aria-hidden":"true"},...l},[...p.map(([k,g])=>n(k,g)),...Array.isArray(a)?a:[a]])});/**
* @license lucide-react v1.24.0 - ISC
*
* This source code is licensed under the ISC license.
* See the LICENSE file in the root directory of this source tree.
*/var B=(t,e)=>{const r=d(({className:o,...s},a)=>n(j,{ref:a,iconNode:e,className:C(`lucide-${L(h(t))}`,`lucide-${t}`,o),...s}));return r.displayName=h(t),r};export{B as t};
